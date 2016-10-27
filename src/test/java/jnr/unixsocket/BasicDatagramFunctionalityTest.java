/*
 * Copyright (C) 2016 Fritz Elfert
 * 
 * (ported from https://github.com/softprops/unisockets/blob/master/unisockets-core/src/main/scala/Socket.scala)
 *
 * This file is part of the JNR project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package jnr.unixsocket;

import jnr.enxio.channels.NativeSelectorProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.channels.Channels;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.Test;

import static junit.framework.Assert.*;

public class BasicDatagramFunctionalityTest {
    private static final String DATA = "foo bar baz. The quick brown fox jumps over the lazy dog. ";
    volatile Throwable serverException;
    volatile long received = 0;

    private UnixSocketAddress makeAddress() throws IOException {
        File socketFile = Files.createTempFile("jnr-unixsocket-test", ".sock").toFile();
        socketFile.delete();
        socketFile.deleteOnExit();
        return new UnixSocketAddress(socketFile);
    }

    private void basicOperation(final long minBytesToSend) throws Throwable {
        serverException = null;
        final StringBuffer rxdata = new StringBuffer();
        final StringBuffer txdata = new StringBuffer();
        final ByteBuffer rxbuf = ByteBuffer.allocate(1024);
        final ByteBuffer txbuf = ByteBuffer.allocate(2024);
        final UnixSocketAddress serverAddress = makeAddress();

        Thread serverThread = new Thread("server side") {
            final UnixDatagramChannel serverChannel = UnixDatagramChannel.open().bind(serverAddress);

            public void run() {
                while (null == serverException) {
                    try {
                        rxbuf.clear();
                        SocketAddress from = serverChannel.receive(rxbuf);
                        rxbuf.flip();
                        int count = rxbuf.limit();
                        rxdata.append(StandardCharsets.UTF_8.decode(rxbuf).toString());
                        received += count;;
                    } catch (IOException ex) {
                        serverException = ex;
                    }
                }
            }
        };
        serverThread.start();

        // client logic
        DatagramChannel clientChannel = UnixDatagramChannel.open();
        received = 0;
        long written = 0;
        while (null == serverException && written < minBytesToSend) {
            txbuf.put(StandardCharsets.UTF_8.encode(DATA));
            txbuf.flip();
            written += clientChannel.send(txbuf, serverAddress);
            txbuf.compact();
            txdata.append(DATA);
            if (null != serverException) {
                throw new Exception().initCause(serverException);
            }
        }
        clientChannel.close();
        while (null == serverException && received < written) {
            Thread.sleep(100);
        }

        assertTrue("More than 0 bytes written", written > 0);
        assertEquals("received", written, received);
        assertEquals("received data", txdata.toString(), rxdata.toString());
    }

    @Test
    public void smallBasicOperationTest() throws Throwable {
        basicOperation(DATA.length());
    }

    @Test
    public void largeBasicOperationTest() throws Throwable {
        basicOperation(1000L * DATA.length());
    }

}
