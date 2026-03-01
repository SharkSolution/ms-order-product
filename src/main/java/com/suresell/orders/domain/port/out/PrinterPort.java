package com.suresell.orders.domain.port.out;
public interface PrinterPort {
    void printBytes(byte[] data);
    void openDrawer();
    boolean isPrinterReady();
}