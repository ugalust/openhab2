/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.irtrans;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IrCommand} is a structure to store and manipulate infrared command
 * in various formats
 *
 * @author Karel Goderis - Initial contribution
 * @since 2.1.0
 *
 */
public class IrCommand {

    private Logger logger = LoggerFactory.getLogger(IrCommand.class);

    /**
     *
     * Each infrared command is in essence a sequence of characters/pointers
     * that refer to pulse/pause timing pairs. So, in order to build an infrared
     * command one has to collate the pulse/pause timings as defined by the
     * sequence
     *
     * PulsePair is a small datastructure to capture each pulse/pair timing pair
     *
     */
    private class PulsePair {
        public int Pulse;
        public int Pause;
    }

    private String remote;
    private String command;
    private String sequence;
    private List<PulsePair> pulsePairs;
    private int numberOfRepeats;
    private int frequency;
    private int frameLength;
    private int pause;
    private boolean startBit;
    private boolean repeatStartBit;
    private boolean noTog;
    private boolean rc5;
    private boolean rc6;

    /**
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * @param logger the logger to set
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * @return the remote
     */
    public String getRemote() {
        return remote;
    }

    /**
     * @param remote the remote to set
     */
    public void setRemote(String remote) {
        this.remote = remote;
    }

    /**
     * @return the command
     */
    public String getCommand() {
        return command;
    }

    /**
     * @param command the command to set
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * @return the sequence
     */
    public String getSequence() {
        return sequence;
    }

    /**
     * @param sequence the sequence to set
     */
    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    /**
     * @return the pulsePairs
     */
    public List<PulsePair> getPulsePairs() {
        return pulsePairs;
    }

    /**
     * @param pulsePairs the pulsePairs to set
     */
    public void setPulsePairs(List<PulsePair> pulsePairs) {
        this.pulsePairs = pulsePairs;
    }

    /**
     * @return the numberOfRepeats
     */
    public int getNumberOfRepeats() {
        return numberOfRepeats;
    }

    /**
     * @param numberOfRepeats the numberOfRepeats to set
     */
    public void setNumberOfRepeats(int numberOfRepeats) {
        this.numberOfRepeats = numberOfRepeats;
    }

    /**
     * @return the frequency
     */
    public int getFrequency() {
        return frequency;
    }

    /**
     * @param frequency the frequency to set
     */
    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    /**
     * @return the frameLength
     */
    public int getFrameLength() {
        return frameLength;
    }

    /**
     * @param frameLength the frameLength to set
     */
    public void setFrameLength(int frameLength) {
        this.frameLength = frameLength;
    }

    /**
     * @return the pause
     */
    public int getPause() {
        return pause;
    }

    /**
     * @param pause the pause to set
     */
    public void setPause(int pause) {
        this.pause = pause;
    }

    /**
     * @return the startBit
     */
    public boolean isStartBit() {
        return startBit;
    }

    /**
     * @param startBit the startBit to set
     */
    public void setStartBit(boolean startBit) {
        this.startBit = startBit;
    }

    /**
     * @return the repeatStartBit
     */
    public boolean isRepeatStartBit() {
        return repeatStartBit;
    }

    /**
     * @param repeatStartBit the repeatStartBit to set
     */
    public void setRepeatStartBit(boolean repeatStartBit) {
        this.repeatStartBit = repeatStartBit;
    }

    /**
     * @return the noTog
     */
    public boolean isNoTog() {
        return noTog;
    }

    /**
     * @param noTog the noTog to set
     */
    public void setNoTog(boolean noTog) {
        this.noTog = noTog;
    }

    /**
     * @return the rc5
     */
    public boolean isRc5() {
        return rc5;
    }

    /**
     * @param rc5 the rc5 to set
     */
    public void setRc5(boolean rc5) {
        this.rc5 = rc5;
    }

    /**
     * @return the rc6
     */
    public boolean isRc6() {
        return rc6;
    }

    /**
     * @param rc6 the rc6 to set
     */
    public void setRc6(boolean rc6) {
        this.rc6 = rc6;
    }

    /**
     * Matches two IrCommands Commands match if they have the same remote and
     * the same command
     *
     * @param anotherCommand
     *            the another command
     * @return true, if successful
     */
    public boolean matches(IrCommand anotherCommand) {
        return (matchRemote(anotherCommand) && matchCommand(anotherCommand));
    }

    /**
     * Match remote fields of two IrCommands In everything we do in the IRtrans
     * binding, the "*" stands for a wilcard character and will match anything
     *
     * @param S
     *            the s
     * @return true, if successful
     */
    private boolean matchRemote(IrCommand S) {
        return "*".equals(remote) || "*".equals(S.remote) || S.remote.equals(remote);
    }

    /**
     * Match command fields of two IrCommands
     *
     * @param S
     *            the s
     * @return true, if successful
     */
    private boolean matchCommand(IrCommand S) {
        return "*".equals(command) || "*".equals(S.command) || S.command.equals(command);
    }

    /**
     * Convert/Parse the IRCommand into a ByteBuffer that is compatible with the
     * IRTrans devices
     *
     * @return the byte buffer
     */
    public ByteBuffer toByteBuffer() {

        ByteBuffer byteBuffer = ByteBuffer.allocate(44 + 210 + 1);

        // skip first byte for length - we will fill it in at the end
        byteBuffer.position(1);

        // Checksum - 1 byte - not used in the ethernet version of the device
        byteBuffer.put((byte) 0);

        // Command - 1 byte - not used
        byteBuffer.put((byte) 0);

        // Address - 1 byte - not used
        byteBuffer.put((byte) 0);

        // Mask - 2 bytes - not used
        byteBuffer.putShort((short) 0);

        // Number of pulse pairs - 1 byte

        byte[] byteSequence = sequence.getBytes(StandardCharsets.US_ASCII);
        byteBuffer.put((byte) (byteSequence.length));

        // Frequency - 1 byte
        byteBuffer.put((byte) frequency);

        // Mode / Flags - 1 byte
        byte modeFlags = 0;
        if (startBit) {
            modeFlags = (byte) (modeFlags | 1);
        }
        if (repeatStartBit) {
            modeFlags = (byte) (modeFlags | 2);
        }
        if (rc5) {
            modeFlags = (byte) (modeFlags | 4);
        }
        if (rc6) {
            modeFlags = (byte) (modeFlags | 8);
        }
        byteBuffer.put(modeFlags);

        // Pause timings - 8 Shorts = 16 bytes
        for (int i = 0; i < pulsePairs.size(); i++) {
            byteBuffer.putShort((short) Math.round(pulsePairs.get(i).Pause / 8));
        }
        for (int i = pulsePairs.size(); i <= 7; i++) {
            byteBuffer.putShort((short) 0);
        }

        // Pulse timings - 8 Shorts = 16 bytes
        for (int i = 0; i < pulsePairs.size(); i++) {
            byteBuffer.putShort((short) Math.round(pulsePairs.get(i).Pulse / 8));
        }
        for (int i = pulsePairs.size(); i <= 7; i++) {
            byteBuffer.putShort((short) 0);
        }

        // Time Counts - 1 Byte
        byteBuffer.put((byte) pulsePairs.size());

        // Repeats - 1 Byte
        byte repeat = (byte) 0;
        repeat = (byte) numberOfRepeats;
        if (frameLength > 0) {
            repeat = (byte) (repeat | 128);
        }
        byteBuffer.put(repeat);

        // Repeat Pause or Frame Length - 1 byte
        if ((repeat & 128) == 128) {
            byteBuffer.put((byte) frameLength);
        } else {
            byteBuffer.put((byte) pause);
        }

        // IR pulse sequence
        try {
            byteBuffer.put(sequence.getBytes("ASCII"));
        } catch (UnsupportedEncodingException e) {
            logger.warn("An exception occurred while encoding the sequence : '{}'", e.getMessage(), e);
        }

        // Add <CR> (ASCII 13) at the end of the sequence
        byteBuffer.put((byte) ((char) 13));

        // set the length of the byte sequence
        byteBuffer.flip();
        byteBuffer.position(0);
        byteBuffer.put((byte) (byteBuffer.limit() - 1));
        byteBuffer.position(0);

        return byteBuffer;

    }

    /**
     * Convert the the infrared command to a Hexadecimal notation/string that
     * can be interpreted by the IRTrans device
     *
     * Convert the first 44 bytes to hex notation, then copy the remainder (= IR
     * command piece) as ASCII string
     *
     * @return the byte buffer in Hex format
     */
    public ByteBuffer toHEXByteBuffer() {

        byte hexDigit[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

        ByteBuffer byteBuffer = toByteBuffer();
        byte[] toConvert = new byte[byteBuffer.limit()];
        byteBuffer.get(toConvert, 0, byteBuffer.limit());

        byte[] converted = new byte[toConvert.length * 2];

        for (int k = 0; k < toConvert.length - 1; k++) {
            converted[2 * k] = hexDigit[(toConvert[k] >> 4) & 0x0f];
            converted[2 * k + 1] = hexDigit[toConvert[k] & 0x0f];

        }

        ByteBuffer convertedBuffer = ByteBuffer.allocate(converted.length);
        convertedBuffer.put(converted);
        convertedBuffer.flip();

        return convertedBuffer;

    }

    /**
     * Convert 'sequence' bit of the IRTrans compatible byte buffer to a
     * Hexidecimal string
     *
     * @return the string
     */
    public String sequenceToHEXString() {
        byte[] byteArray = toHEXByteArray();
        return new String(byteArray, 88, byteArray.length - 88 - 2);
    }

    /**
     * Convert the IRTrans compatible byte buffer to a string
     *
     * @return the string
     */
    public String toHEXString() {
        return new String(toHEXByteArray());
    }

    /**
     * Convert the IRTrans compatible byte buffer to a byte array.
     *
     * @return the byte[]
     */
    public byte[] toHEXByteArray() {
        return toHEXByteBuffer().array();
    }

}
