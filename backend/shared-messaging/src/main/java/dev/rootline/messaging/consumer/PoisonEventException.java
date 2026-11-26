package dev.rootline.messaging.consumer;

/** 這則訊息本身有問題（格式錯、缺欄位），重試也不會成功，應直接送進 dead-letter topic。 */
public class PoisonEventException extends RuntimeException {

    public PoisonEventException(String message) {
        super(message);
    }

    public PoisonEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
