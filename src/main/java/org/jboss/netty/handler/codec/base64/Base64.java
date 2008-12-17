/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
/*
 * Written by Robert Harder and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */
package org.jboss.netty.handler.codec.base64;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * Utility class for {@link ChannelBuffer} that encodes and decodes to and from
 * <a href="http://en.wikipedia.org/wiki/Base64">Base64</a> notation.
 * <p>
 * The encoding and decoding algorithm in this class has been derived from
 * <a href="http://iharder.sourceforge.net/current/java/base64/">Robert Harder's Public Domain Base64 Encoder/Decoder</a>.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Robert Harder (rob@iharder.net)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class Base64 {

    /** Maximum line length (76) of Base64 output. */
    private final static int MAX_LINE_LENGTH = 76;

    /** The equals sign (=) as a byte. */
    private final static byte EQUALS_SIGN = (byte) '=';

    /** The new line character (\n) as a byte. */
    private final static byte NEW_LINE = (byte) '\n';

    private final static byte WHITE_SPACE_ENC = -5; // Indicates white space in encoding

    private final static byte EQUALS_SIGN_ENC = -1; // Indicates equals sign in encoding

    private final static byte[] getAlphabet(Base64Dialect dialect) {
        if (dialect == null) {
            throw new NullPointerException("dialect");
        }
        return dialect.alphabet;
    }

    private final static byte[] getDecodabet(Base64Dialect dialect) {
        if (dialect == null) {
            throw new NullPointerException("dialect");
        }
        return dialect.decodabet;
    }

    public static ChannelBuffer encode(ChannelBuffer src) {
        return encode(src, true);
    }

    public static ChannelBuffer encode(ChannelBuffer src, boolean breakLines) {
        return encode(src, breakLines, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, boolean breakLines, Base64Dialect dialect) {
        ChannelBuffer dest = encode(
                src, src.readerIndex(), src.readableBytes(), breakLines, dialect);
        src.readerIndex(src.writerIndex());
        return dest;
    }

    public static ChannelBuffer encode(ChannelBuffer src, int off, int len) {
        return encode(src, off, len, true);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, int off, int len, boolean breakLines) {
        return encode(src, off, len, breakLines, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, int off, int len,
            boolean breakLines, Base64Dialect dialect) {

        int len43 = len * 4 / 3;
        byte[] dest = new byte[len43 + (len % 3 > 0? 4 : 0) + // Account for padding
                (breakLines? len43 / MAX_LINE_LENGTH : 0)]; // New lines
        int d = 0;
        int e = 0;
        int len2 = len - 2;
        int lineLength = 0;
        for (; d < len2; d += 3, e += 4) {
            encode3to4(src, d + off, 3, dest, e, dialect);

            lineLength += 4;
            if (breakLines && lineLength == MAX_LINE_LENGTH) {
                dest[e + 4] = NEW_LINE;
                e ++;
                lineLength = 0;
            } // end if: end of line
        } // end for: each piece of array

        if (d < len) {
            encode3to4(src, d + off, len - d, dest, e, dialect);
            e += 4;
        } // end if: some padding needed

        return ChannelBuffers.wrappedBuffer(dest, 0, e);
    }

    private static byte[] encode3to4(
            ChannelBuffer src, int srcOffset, int numSigBytes,
            byte[] dest, int destOffset, Base64Dialect dialect) {

        byte[] ALPHABET = getAlphabet(dialect);

        //           1         2         3
        // 01234567890123456789012345678901 Bit position
        // --------000000001111111122222222 Array position from threeBytes
        // --------|    ||    ||    ||    | Six bit groups to index ALPHABET
        //          >>18  >>12  >> 6  >> 0  Right shift necessary
        //                0x3f  0x3f  0x3f  Additional AND

        // Create buffer with zero-padding if there are only one or two
        // significant bytes passed in the array.
        // We have to shift left 24 in order to flush out the 1's that appear
        // when Java treats a value as negative that is cast from a byte to an int.
        int inBuff =
                (numSigBytes > 0? src.getByte(srcOffset    ) << 24 >>>  8 : 0) |
                (numSigBytes > 1? src.getByte(srcOffset + 1) << 24 >>> 16 : 0) |
                (numSigBytes > 2? src.getByte(srcOffset + 2) << 24 >>> 24 : 0);

        switch (numSigBytes) {
        case 3:
            dest[destOffset    ] = ALPHABET[inBuff >>> 18       ];
            dest[destOffset + 1] = ALPHABET[inBuff >>> 12 & 0x3f];
            dest[destOffset + 2] = ALPHABET[inBuff >>>  6 & 0x3f];
            dest[destOffset + 3] = ALPHABET[inBuff        & 0x3f];
            break;
        case 2:
            dest[destOffset    ] = ALPHABET[inBuff >>> 18       ];
            dest[destOffset + 1] = ALPHABET[inBuff >>> 12 & 0x3f];
            dest[destOffset + 2] = ALPHABET[inBuff >>> 6  & 0x3f];
            dest[destOffset + 3] = EQUALS_SIGN;
            break;
        case 1:
            dest[destOffset    ] = ALPHABET[inBuff >>> 18       ];
            dest[destOffset + 1] = ALPHABET[inBuff >>> 12 & 0x3f];
            dest[destOffset + 2] = EQUALS_SIGN;
            dest[destOffset + 3] = EQUALS_SIGN;
            break;
        }
        return dest;
    }

    public static ChannelBuffer decode(ChannelBuffer src) {
        return decode(src, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer decode(ChannelBuffer src, Base64Dialect dialect) {
        ChannelBuffer dest = decode(src, src.readerIndex(), src.readableBytes(), dialect);
        src.readerIndex(src.writerIndex());
        return dest;
    }

    public static ChannelBuffer decode(
            ChannelBuffer src, int off, int len) {
        return decode(src, off, len, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer decode(
            ChannelBuffer src, int off, int len, Base64Dialect dialect) {

        byte[] DECODABET = getDecodabet(dialect);

        int len34 = len * 3 / 4;
        byte[] dest = new byte[len34]; // Upper limit on size of output
        int outBuffPosn = 0;

        byte[] b4 = new byte[4];
        int b4Posn = 0;
        int i = 0;
        byte sbiCrop = 0;
        byte sbiDecode = 0;
        for (i = off; i < off + len; i ++) {
            sbiCrop = (byte) (src.getByte(i) & 0x7f); // Only the low seven bits
            sbiDecode = DECODABET[sbiCrop];

            if (sbiDecode >= WHITE_SPACE_ENC) { // White space, Equals sign or better
                if (sbiDecode >= EQUALS_SIGN_ENC) {
                    b4[b4Posn ++] = sbiCrop;
                    if (b4Posn > 3) {
                        outBuffPosn += decode4to3(
                                b4, 0, dest, outBuffPosn, dialect);
                        b4Posn = 0;

                        // If that was the equals sign, break out of 'for' loop
                        if (sbiCrop == EQUALS_SIGN) {
                            break;
                        }
                    } // end if: quartet built
                } // end if: equals sign or better
            } // end if: white space, equals sign or better
            else {
                throw new IllegalArgumentException(
                        "bad Base64 input character at " + i + ": " +
                        src.getUnsignedByte(i) + " (decimal)");
            }
        }

        return ChannelBuffers.wrappedBuffer(dest, 0, outBuffPosn);
    }

    private static int decode4to3(
            byte[] src, int srcOffset,
            byte[] dest, int destOffset, Base64Dialect dialect) {

        byte[] DECODABET = getDecodabet(dialect);

        // Example: Dk==
        if (src[srcOffset + 2] == EQUALS_SIGN) {
            int outBuff =
                    (DECODABET[src[srcOffset    ]] & 0xFF) << 18 |
                    (DECODABET[src[srcOffset + 1]] & 0xFF) << 12;

            dest[destOffset] = (byte) (outBuff >>> 16);
            return 1;
        }

        // Example: DkL=
        else if (src[srcOffset + 3] == EQUALS_SIGN) {
            int outBuff =
                    (DECODABET[src[srcOffset    ]] & 0xFF) << 18 |
                    (DECODABET[src[srcOffset + 1]] & 0xFF) << 12 |
                    (DECODABET[src[srcOffset + 2]] & 0xFF) <<  6;

            dest[destOffset    ] = (byte) (outBuff >>> 16);
            dest[destOffset + 1] = (byte) (outBuff >>>  8);
            return 2;
        }

        // Example: DkLE
        else {
            try {
                int outBuff =
                        (DECODABET[src[srcOffset    ]] & 0xFF) << 18 |
                        (DECODABET[src[srcOffset + 1]] & 0xFF) << 12 |
                        (DECODABET[src[srcOffset + 2]] & 0xFF) <<  6 |
                         DECODABET[src[srcOffset + 3]] & 0xFF;

                dest[destOffset    ] = (byte) (outBuff >> 16);
                dest[destOffset + 1] = (byte) (outBuff >>  8);
                dest[destOffset + 2] = (byte)  outBuff;
                return 3;
            } catch (Exception e) {
                throw new IllegalArgumentException("not encoded in Base64");
            }
        }
    }

    private Base64() {
        // Unused
    }
}