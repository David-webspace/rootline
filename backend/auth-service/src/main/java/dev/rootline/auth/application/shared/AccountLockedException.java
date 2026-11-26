package dev.rootline.auth.application.shared;

/** 密碼正確但帳號已被鎖定。只有知道正確密碼的人才會看到這個訊息。 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Account is locked after too many failed attempts; contact your administrator");
    }
}
