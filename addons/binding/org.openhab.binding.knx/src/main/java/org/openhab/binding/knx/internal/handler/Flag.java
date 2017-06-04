package org.openhab.binding.knx.internal.handler;

public enum Flag {
    READ('R'),
    WRITE('W'),
    TRANSMIT('T'),
    UPDATE('U');

    private char flag;

    private Flag(char flag) {
        this.flag = flag;
    }

    public static Flag getFlag(char c) {
        for (Flag f : Flag.values()) {
            if (f.flag == c) {
                return f;
            }
        }
        return null;
    }

}
