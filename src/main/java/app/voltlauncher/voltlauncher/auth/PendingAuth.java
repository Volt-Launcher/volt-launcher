package app.voltlauncher.voltlauncher.auth;

public final class PendingAuth {

    private final String codeVerifier;
    private volatile AuthFlowStatus status = AuthFlowStatus.PENDING;
    private volatile AuthResult result;
    private volatile String errorMessage;

    public PendingAuth(String codeVerifier) {
        this.codeVerifier = codeVerifier;
    }

    public String codeVerifier() {
        return codeVerifier;
    }

    public AuthFlowStatus status() {
        return status;
    }

    public AuthResult result() {
        return result;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public boolean isPending() {
        return status == AuthFlowStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == AuthFlowStatus.SUCCESS;
    }

    public boolean hasFailed() {
        return status == AuthFlowStatus.ERROR;
    }

    public boolean isTerminal() {
        return status != AuthFlowStatus.PENDING;
    }

    public synchronized void complete(AuthResult result) {
        if (isTerminal()) return;
        this.result = result;
        this.errorMessage = null;
        this.status = AuthFlowStatus.SUCCESS;
    }

    public synchronized void fail(String message) {
        if (isTerminal()) return;
        this.result = null;
        this.errorMessage = message;
        this.status = AuthFlowStatus.ERROR;
    }
}