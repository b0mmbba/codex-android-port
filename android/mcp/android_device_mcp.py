#!/usr/bin/env python3
"""Small stdio MCP server for controlling the local Android device."""

import json
import subprocess
import sys
from typing import Any


SERVER_INFO = {"name": "android-device", "version": "0.1.0"}


def run_command(args: list[str], timeout: int = 20) -> dict[str, Any]:
    try:
        completed = subprocess.run(
            args,
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        return {"ok": False, "error": f"missing command: {exc.filename}"}
    except subprocess.TimeoutExpired:
        return {"ok": False, "error": f"timed out after {timeout}s"}

    return {
        "ok": completed.returncode == 0,
        "exit_code": completed.returncode,
        "stdout": completed.stdout,
        "stderr": completed.stderr,
    }


def text_content(value: Any) -> dict[str, Any]:
    if not isinstance(value, str):
        value = json.dumps(value, ensure_ascii=False, indent=2)
    return {"content": [{"type": "text", "text": value}]}


def require_int(args: dict[str, Any], name: str) -> int:
    value = args.get(name)
    if not isinstance(value, int):
        raise ValueError(f"`{name}` must be an integer")
    return value


def require_str(args: dict[str, Any], name: str) -> str:
    value = args.get(name)
    if not isinstance(value, str) or not value:
        raise ValueError(f"`{name}` must be a non-empty string")
    return value


def android_input_text(value: str) -> str:
    return value.replace("%", "%25").replace(" ", "%s")


def call_tool(name: str, args: dict[str, Any]) -> dict[str, Any]:
    if name == "screen_state":
        window = run_command(["dumpsys", "window", "windows"], timeout=10)
        power = run_command(["dumpsys", "power"], timeout=10)
        return text_content({"window": window, "power": power})

    if name == "dump_ui":
        path = args.get("path", "/sdcard/window_dump.xml")
        if not isinstance(path, str) or not path.startswith("/"):
            raise ValueError("`path` must be an absolute device path")
        dump = run_command(["uiautomator", "dump", path], timeout=15)
        contents = run_command(["cat", path], timeout=15)
        return text_content({"dump": dump, "xml": contents.get("stdout", "")})

    if name == "screenshot":
        path = args.get("path", "/sdcard/Download/codex-screen.png")
        if not isinstance(path, str) or not path.startswith("/"):
            raise ValueError("`path` must be an absolute device path")
        result = run_command(["screencap", "-p", path], timeout=15)
        result["path"] = path
        return text_content(result)

    if name == "tap":
        x = require_int(args, "x")
        y = require_int(args, "y")
        return text_content(run_command(["input", "tap", str(x), str(y)], timeout=5))

    if name == "swipe":
        x1 = require_int(args, "x1")
        y1 = require_int(args, "y1")
        x2 = require_int(args, "x2")
        y2 = require_int(args, "y2")
        duration_ms = int(args.get("duration_ms", 300))
        return text_content(
            run_command(
                ["input", "swipe", str(x1), str(y1), str(x2), str(y2), str(duration_ms)],
                timeout=10,
            )
        )

    if name == "keyevent":
        key = require_str(args, "key")
        return text_content(run_command(["input", "keyevent", key], timeout=5))

    if name == "type_text":
        value = require_str(args, "text")
        return text_content(run_command(["input", "text", android_input_text(value)], timeout=10))

    if name == "start_activity":
        action = args.get("action")
        data = args.get("data")
        component = args.get("component")
        command = ["am", "start"]
        if isinstance(action, str) and action:
            command.extend(["-a", action])
        if isinstance(data, str) and data:
            command.extend(["-d", data])
        if isinstance(component, str) and component:
            command.extend(["-n", component])
        if len(command) == 2:
            raise ValueError("provide at least one of `action`, `data`, or `component`")
        return text_content(run_command(command, timeout=15))

    if name == "list_packages":
        prefix = args.get("prefix")
        command = ["pm", "list", "packages"]
        result = run_command(command, timeout=20)
        if isinstance(prefix, str) and prefix:
            packages = [
                line for line in result.get("stdout", "").splitlines() if prefix in line
            ]
            result["stdout"] = "\n".join(packages)
        return text_content(result)

    raise ValueError(f"unknown tool: {name}")


def tool(name: str, description: str, properties: dict[str, Any], required: list[str] | None = None) -> dict[str, Any]:
    return {
        "name": name,
        "description": description,
        "inputSchema": {
            "type": "object",
            "properties": properties,
            "required": required or [],
            "additionalProperties": False,
        },
    }


TOOLS = [
    tool("screen_state", "Read focused window and power state from dumpsys.", {}),
    tool(
        "dump_ui",
        "Dump the current Android UI hierarchy as XML.",
        {"path": {"type": "string", "description": "Absolute output path on the device."}},
    ),
    tool(
        "screenshot",
        "Save a screenshot PNG on the device.",
        {"path": {"type": "string", "description": "Absolute output path on the device."}},
    ),
    tool(
        "tap",
        "Tap screen coordinates.",
        {"x": {"type": "integer"}, "y": {"type": "integer"}},
        ["x", "y"],
    ),
    tool(
        "swipe",
        "Swipe between screen coordinates.",
        {
            "x1": {"type": "integer"},
            "y1": {"type": "integer"},
            "x2": {"type": "integer"},
            "y2": {"type": "integer"},
            "duration_ms": {"type": "integer"},
        },
        ["x1", "y1", "x2", "y2"],
    ),
    tool("keyevent", "Send an Android key event.", {"key": {"type": "string"}}, ["key"]),
    tool("type_text", "Type text into the focused input field.", {"text": {"type": "string"}}, ["text"]),
    tool(
        "start_activity",
        "Start an Android activity or intent.",
        {
            "action": {"type": "string"},
            "data": {"type": "string"},
            "component": {"type": "string"},
        },
    ),
    tool(
        "list_packages",
        "List installed Android packages.",
        {"prefix": {"type": "string", "description": "Optional substring filter."}},
    ),
]


def handle(request: dict[str, Any]) -> dict[str, Any] | None:
    request_id = request.get("id")
    method = request.get("method")

    try:
        if method == "initialize":
            result = {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {}},
                "serverInfo": SERVER_INFO,
            }
        elif method == "notifications/initialized":
            return None
        elif method == "tools/list":
            result = {"tools": TOOLS}
        elif method == "tools/call":
            params = request.get("params", {})
            if not isinstance(params, dict):
                raise ValueError("`params` must be an object")
            name = require_str(params, "name")
            arguments = params.get("arguments", {})
            if not isinstance(arguments, dict):
                raise ValueError("`arguments` must be an object")
            result = call_tool(name, arguments)
        else:
            return {
                "jsonrpc": "2.0",
                "id": request_id,
                "error": {"code": -32601, "message": f"method not found: {method}"},
            }
        return {"jsonrpc": "2.0", "id": request_id, "result": result}
    except Exception as exc:
        return {
            "jsonrpc": "2.0",
            "id": request_id,
            "error": {"code": -32000, "message": str(exc)},
        }


def main() -> int:
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError as exc:
            response = {
                "jsonrpc": "2.0",
                "id": None,
                "error": {"code": -32700, "message": str(exc)},
            }
        else:
            response = handle(request)
        if response is not None:
            print(json.dumps(response, ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
