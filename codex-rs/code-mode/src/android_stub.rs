use std::path::PathBuf;
use std::sync::Arc;

use codex_code_mode_protocol::CellId;
use codex_code_mode_protocol::CodeModeNestedToolCall;
use codex_code_mode_protocol::CodeModeSession;
use codex_code_mode_protocol::CodeModeSessionDelegate;
use codex_code_mode_protocol::CodeModeSessionProvider;
use codex_code_mode_protocol::CodeModeSessionProviderFuture;
use codex_code_mode_protocol::CodeModeSessionResultFuture;
use codex_code_mode_protocol::ExecuteRequest;
use codex_code_mode_protocol::NotificationFuture;
use codex_code_mode_protocol::RuntimeResponse;
use codex_code_mode_protocol::StartedCell;
use codex_code_mode_protocol::ToolInvocationFuture;
use codex_code_mode_protocol::WaitOutcome;
use codex_code_mode_protocol::WaitRequest;
use tokio_util::sync::CancellationToken;

const CODE_MODE_UNAVAILABLE: &str = "code mode is not available in the Android build";

/// Controls whether V8 may generate executable code at runtime.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum V8JitMode {
    #[default]
    Enabled,
    Disabled,
}

/// Android builds do not bundle V8, so initialization succeeds as a no-op.
pub fn initialize_v8(_jit_mode: V8JitMode) -> Result<(), String> {
    Ok(())
}

pub struct NoopCodeModeSessionDelegate;

impl CodeModeSessionDelegate for NoopCodeModeSessionDelegate {
    fn invoke_tool<'a>(
        &'a self,
        _invocation: CodeModeNestedToolCall,
        cancellation_token: CancellationToken,
    ) -> ToolInvocationFuture<'a> {
        Box::pin(async move {
            cancellation_token.cancelled().await;
            Err("code mode nested tools are unavailable".to_string())
        })
    }

    fn notify<'a>(
        &'a self,
        _call_id: String,
        _cell_id: CellId,
        _text: String,
        _cancellation_token: CancellationToken,
    ) -> NotificationFuture<'a> {
        Box::pin(async { Ok(()) })
    }

    fn cell_closed(&self, _cell_id: &CellId) {}
}

#[derive(Default)]
pub struct InProcessCodeModeSessionProvider;

impl CodeModeSessionProvider for InProcessCodeModeSessionProvider {
    fn create_session<'a>(
        &'a self,
        _delegate: Arc<dyn CodeModeSessionDelegate>,
    ) -> CodeModeSessionProviderFuture<'a> {
        Box::pin(async {
            let session: Arc<dyn CodeModeSession> = Arc::new(InProcessCodeModeSession::new());
            Ok(session)
        })
    }
}

pub struct InProcessCodeModeSession;

impl InProcessCodeModeSession {
    pub fn new() -> Self {
        Self
    }

    pub fn with_delegate(_delegate: Arc<dyn CodeModeSessionDelegate>) -> Self {
        Self
    }
}

impl Default for InProcessCodeModeSession {
    fn default() -> Self {
        Self::new()
    }
}

impl CodeModeSession for InProcessCodeModeSession {
    fn execute<'a>(
        &'a self,
        _request: ExecuteRequest,
    ) -> CodeModeSessionResultFuture<'a, StartedCell> {
        Box::pin(async { Err(CODE_MODE_UNAVAILABLE.to_string()) })
    }

    fn wait<'a>(&'a self, request: WaitRequest) -> CodeModeSessionResultFuture<'a, WaitOutcome> {
        Box::pin(async move {
            Ok(WaitOutcome::MissingCell(RuntimeResponse::Result {
                cell_id: request.cell_id,
                content_items: Vec::new(),
                error_text: Some(CODE_MODE_UNAVAILABLE.to_string()),
            }))
        })
    }

    fn terminate<'a>(&'a self, cell_id: CellId) -> CodeModeSessionResultFuture<'a, WaitOutcome> {
        Box::pin(async move {
            Ok(WaitOutcome::MissingCell(RuntimeResponse::Terminated {
                cell_id,
                content_items: Vec::new(),
            }))
        })
    }

    fn shutdown<'a>(&'a self) -> CodeModeSessionResultFuture<'a, ()> {
        Box::pin(async { Ok(()) })
    }
}

pub struct ProcessOwnedCodeModeSessionProvider;

impl ProcessOwnedCodeModeSessionProvider {
    pub fn with_host_program(_host_program: PathBuf) -> Self {
        Self
    }
}

impl Default for ProcessOwnedCodeModeSessionProvider {
    fn default() -> Self {
        Self
    }
}

impl CodeModeSessionProvider for ProcessOwnedCodeModeSessionProvider {
    fn create_session<'a>(
        &'a self,
        _delegate: Arc<dyn CodeModeSessionDelegate>,
    ) -> CodeModeSessionProviderFuture<'a> {
        Box::pin(async {
            let session: Arc<dyn CodeModeSession> = Arc::new(ProcessOwnedCodeModeSession::new());
            Ok(session)
        })
    }
}

pub struct ProcessOwnedCodeModeSession;

impl ProcessOwnedCodeModeSession {
    pub fn new() -> Self {
        Self
    }
}

impl Default for ProcessOwnedCodeModeSession {
    fn default() -> Self {
        Self::new()
    }
}

impl CodeModeSession for ProcessOwnedCodeModeSession {
    fn execute<'a>(
        &'a self,
        _request: ExecuteRequest,
    ) -> CodeModeSessionResultFuture<'a, StartedCell> {
        Box::pin(async { Err(CODE_MODE_UNAVAILABLE.to_string()) })
    }

    fn wait<'a>(&'a self, request: WaitRequest) -> CodeModeSessionResultFuture<'a, WaitOutcome> {
        Box::pin(async move {
            Ok(WaitOutcome::MissingCell(RuntimeResponse::Result {
                cell_id: request.cell_id,
                content_items: Vec::new(),
                error_text: Some(CODE_MODE_UNAVAILABLE.to_string()),
            }))
        })
    }

    fn terminate<'a>(&'a self, cell_id: CellId) -> CodeModeSessionResultFuture<'a, WaitOutcome> {
        Box::pin(async move {
            Ok(WaitOutcome::MissingCell(RuntimeResponse::Terminated {
                cell_id,
                content_items: Vec::new(),
            }))
        })
    }

    fn shutdown<'a>(&'a self) -> CodeModeSessionResultFuture<'a, ()> {
        Box::pin(async { Ok(()) })
    }
}
