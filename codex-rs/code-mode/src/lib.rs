#[cfg(target_os = "android")]
mod android_stub;
#[cfg(not(target_os = "android"))]
mod cell_actor;
#[cfg(not(target_os = "android"))]
mod remote_session;
#[cfg(not(target_os = "android"))]
mod runtime;
#[cfg(not(target_os = "android"))]
mod service;
#[cfg(not(target_os = "android"))]
mod session_runtime;
#[cfg(not(target_os = "android"))]
mod v8_init;

#[cfg(not(target_os = "android"))]
pub(crate) type TaskFailureHandler = std::sync::Arc<dyn Fn(String) + Send + Sync>;

#[cfg(target_os = "android")]
pub use android_stub::InProcessCodeModeSession;
#[cfg(target_os = "android")]
pub use android_stub::InProcessCodeModeSessionProvider;
#[cfg(target_os = "android")]
pub use android_stub::NoopCodeModeSessionDelegate;
#[cfg(target_os = "android")]
pub use android_stub::ProcessOwnedCodeModeSession;
#[cfg(target_os = "android")]
pub use android_stub::ProcessOwnedCodeModeSessionProvider;
#[cfg(target_os = "android")]
pub use android_stub::V8JitMode;
#[cfg(target_os = "android")]
pub use android_stub::initialize_v8;
pub use codex_code_mode_protocol::*;
#[cfg(not(target_os = "android"))]
pub use remote_session::ProcessOwnedCodeModeSession;
#[cfg(not(target_os = "android"))]
pub use remote_session::ProcessOwnedCodeModeSessionProvider;
#[cfg(not(target_os = "android"))]
pub use service::InProcessCodeModeSession;
#[cfg(not(target_os = "android"))]
pub use service::InProcessCodeModeSessionProvider;
#[cfg(not(target_os = "android"))]
pub use service::NoopCodeModeSessionDelegate;
#[cfg(not(target_os = "android"))]
pub use v8_init::V8JitMode;
#[cfg(not(target_os = "android"))]
pub use v8_init::initialize_v8;
