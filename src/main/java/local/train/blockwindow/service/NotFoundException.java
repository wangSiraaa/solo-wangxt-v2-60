package local.train.blockwindow.service;

/** 资源不存在（计划号、证件号、保护项 id 等）。 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
