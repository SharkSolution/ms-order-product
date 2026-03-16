package com.suresell.orders.infrastructure.printer;

import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class EscPosBuilder {
    private static final byte[] INIT = {27, 64};
    private static final byte[] CUT_FULL = {29, 86, 65, 0};
    private static final byte[] OPEN_DRAWER = {27, 112, 0, 25, (byte) 250};  
    public byte[] buildReceipt(java.util.function.Consumer<ByteArrayOutputStream> builderConsumer) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            baos.write(INIT);  
            builderConsumer.accept(baos);
            baos.write(CUT_FULL);  
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error construyendo ticket", e);
        }
    }
    public void text(ByteArrayOutputStream bos, String text) {
        try {
            bos.write(text.getBytes("CP850"));
        } catch (IOException e) { throw new RuntimeException(e); }
    }
    public void textLn(ByteArrayOutputStream bos, String text) {
        text(bos, text + "\n");
    }
    public void alignCenter(ByteArrayOutputStream bos) { write(bos, new byte[]{27, 97, 1}); }
    public void alignLeft(ByteArrayOutputStream bos) { write(bos, new byte[]{27, 97, 0}); }
    public void alignRight(ByteArrayOutputStream bos) { write(bos, new byte[]{27, 97, 2}); }
    public void boldOn(ByteArrayOutputStream bos) { write(bos, new byte[]{27, 69, 1}); }
    public void boldOff(ByteArrayOutputStream bos) { write(bos, new byte[]{27, 69, 0}); }
    public void feed(ByteArrayOutputStream bos, int lines) {
        write(bos, new byte[]{27, 100, (byte) lines});
    }
    private void write(ByteArrayOutputStream bos, byte[] command) {
        try { bos.write(command); } catch (IOException e) { throw new RuntimeException(e); }
    }
    public byte[] getOpenDrawerCommand() {
        return OPEN_DRAWER;
    }

    public void inverseOn(ByteArrayOutputStream bos) { write(bos, new byte[]{29, 66, 1}); }
    public void inverseOff(ByteArrayOutputStream bos) { write(bos, new byte[]{29, 66, 0}); }

    public void textSize(ByteArrayOutputStream bos, int widthMultiplier, int heightMultiplier) {
        byte n = (byte) ((widthMultiplier << 4) | heightMultiplier);
        write(bos, new byte[]{29, 33, n});
    }
}
