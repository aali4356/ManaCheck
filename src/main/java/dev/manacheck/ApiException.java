package dev.manacheck;

public class ApiException extends RuntimeException {
  private final int status;
  private final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static ApiException bad(String message) {
    return new ApiException(422, "INVALID_INPUT", message);
  }

  public static ApiException missing(String message) {
    return new ApiException(404, "NOT_FOUND", message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(409, "STALE_REVISION", message);
  }
}
