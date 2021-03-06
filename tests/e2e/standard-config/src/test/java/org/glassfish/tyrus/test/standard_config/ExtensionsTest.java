/*
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
*
* Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
*
* The contents of this file are subject to the terms of either the GNU
* General Public License Version 2 only ("GPL") or the Common Development
* and Distribution License("CDDL") (collectively, the "License").  You
* may not use this file except in compliance with the License.  You can
* obtain a copy of the License at
* http://glassfish.java.net/public/CDDL+GPL_1_1.html
* or packager/legal/LICENSE.txt.  See the License for the specific
* language governing permissions and limitations under the License.
*
* When distributing the software, include this License Header Notice in each
* file and include the License file at packager/legal/LICENSE.txt.
*
* GPL Classpath Exception:
* Oracle designates this particular file as subject to the "Classpath"
* exception as provided by Oracle in the GPL Version 2 section of the License
* file that accompanied this code.
*
* Modifications:
* If applicable, add the following below the License Header, with the fields
* enclosed by brackets [] replaced by your own identifying information:
* "Portions Copyright [year] [name of copyright owner]"
*
* Contributor(s):
* If you wish your version of this file to be governed by only the CDDL or
* only the GPL Version 2, indicate your decision by adding "[Contributor]
* elects to include this software in this distribution under the [CDDL or GPL
* Version 2] license."  If you don't indicate a single choice of license, a
* recipient has the option to distribute your version of this file under
* either the CDDL, the GPL Version 2 or to extend the choice of license to
* its licensees as provided above.  However, if you add GPL Version 2 code
* and therefore, elected the GPL Version 2 license, then the option applies
* only if the new code is made subject to such option by the copyright
* holder.
*/
package org.glassfish.tyrus.test.standard_config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.core.TyrusExtension;
import org.glassfish.tyrus.server.Server;
import org.glassfish.tyrus.test.tools.TestContainer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
@SuppressWarnings("serial")
public class ExtensionsTest extends TestContainer {

    private static final String SENT_MESSAGE = "Always pass on what you have learned.";

    private static final String MULTIPLE_REQUEST_EXTENSION_NAME = "testExtension";

    @ServerEndpoint(value = "/extensionsTest", configurator = MyServerConfigurator.class)
    public static class ExtensionsTestEndpoint {
        @OnOpen
        public void onOpen(Session s) {
            for (Extension extension : s.getNegotiatedExtensions()) {
                if (extension.getName().equals("ext1") || extension.getName().equals("ext2")) {
                    try {
                        s.getBasicRemote().sendText(SENT_MESSAGE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    public static class MyServerConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
            return requested;
        }
    }

    @Test
    public void testExtensions() throws DeploymentException {
        Server server = startServer(ExtensionsTestEndpoint.class);

        try {
            final List<Extension.Parameter> list1 = new ArrayList<Extension.Parameter>() {{
                add(new TyrusExtension.TyrusParameter("prop1", "val1"));
                add(new TyrusExtension.TyrusParameter("prop2", "val2"));
                add(new TyrusExtension.TyrusParameter("prop3", "val3"));
            }};

            final List<Extension.Parameter> list2 = new ArrayList<Extension.Parameter>() {{
                add(new TyrusExtension.TyrusParameter("prop1", "val1"));
                add(new TyrusExtension.TyrusParameter("prop2", "val2"));
                add(new TyrusExtension.TyrusParameter("prop3", "val3"));
            }};

            ArrayList<Extension> extensions = new ArrayList<Extension>();
            extensions.add(new TyrusExtension("ext1", list1));
            extensions.add(new TyrusExtension("ext2", list2));

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().extensions(extensions).build();

            final CountDownLatch messageLatch = new CountDownLatch(4);
            ClientManager client = createClient();
            ExtensionsClientEndpoint clientEndpoint = new ExtensionsClientEndpoint(messageLatch);
            client.connectToServer(clientEndpoint, clientConfiguration,
                    getURI(ExtensionsTestEndpoint.class));

            messageLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, messageLatch.getCount());
            assertEquals(SENT_MESSAGE, clientEndpoint.getReceivedMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    private static class ExtensionsClientEndpoint extends Endpoint {

        private final CountDownLatch messageLatch;
        private volatile String receivedMessage;

        private ExtensionsClientEndpoint(CountDownLatch messageLatch) {
            this.messageLatch = messageLatch;
        }

        @Override
        public void onOpen(final Session session, EndpointConfig EndpointConfig) {
            try {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                        receivedMessage = message;
                        for (Extension extension : session.getNegotiatedExtensions()) {
                            if (extension.getName().equals("ext1") || extension.getName().equals("ext2")) {
                                messageLatch.countDown();
                            }
                        }
                    }
                });

                session.getBasicRemote().sendText(SENT_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String getReceivedMessage() {
            return receivedMessage;
        }
    }

    @ServerEndpoint(value = "/multipleRequestExtensionsTest", configurator = MultipleRequestExtensionsConfigurator.class)
    public static class MultipleRequestExtensionsTestEndpoint {
        @OnOpen
        public void onOpen(Session s) {
            final List<Extension> negotiatedExtensions = s.getNegotiatedExtensions();
            if (negotiatedExtensions.size() == 1 && negotiatedExtensions.get(0).getName().equals(MULTIPLE_REQUEST_EXTENSION_NAME)) {
                try {
                    s.getBasicRemote().sendText(SENT_MESSAGE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    public static class MultipleRequestExtensionsConfigurator extends ServerEndpointConfig.Configurator {

        private static final List<Extension> installedExtensions = new ArrayList<Extension>() {{
            add(new TyrusExtension(MULTIPLE_REQUEST_EXTENSION_NAME));
        }};

        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
            return super.getNegotiatedExtensions(installedExtensions, requested);
        }
    }

    @Test
    public void testMultipleRequestExtensions() throws DeploymentException {
        Server server = startServer(MultipleRequestExtensionsTestEndpoint.class);

        // parameter list is not relevant for this testcase
        final List<Extension.Parameter> parameterList = new ArrayList<Extension.Parameter>() {{
            add(new TyrusExtension.TyrusParameter("prop1", "val1"));
            add(new TyrusExtension.TyrusParameter("prop2", "val2"));
            add(new TyrusExtension.TyrusParameter("prop3", "val3"));
        }};

        try {
            ArrayList<Extension> extensions = new ArrayList<Extension>();
            extensions.add(new TyrusExtension(MULTIPLE_REQUEST_EXTENSION_NAME, null));
            extensions.add(new TyrusExtension(MULTIPLE_REQUEST_EXTENSION_NAME, parameterList));

            final ClientEndpointConfig clientConfiguration = ClientEndpointConfig.Builder.create().extensions(extensions).build();

            ClientManager client = createClient();
            final CountDownLatch clientLatch = new CountDownLatch(2);
            MultipleRequestExtensionsClientEndpoint clientEndpoint =
                    new MultipleRequestExtensionsClientEndpoint(clientLatch);
            client.connectToServer(clientEndpoint, clientConfiguration,
                    getURI(MultipleRequestExtensionsTestEndpoint.class));

            clientLatch.await(1, TimeUnit.SECONDS);
            assertEquals(0, clientLatch.getCount());
            assertEquals(SENT_MESSAGE, clientEndpoint.getReceivedMessage());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            stopServer(server);
        }
    }

    private static class MultipleRequestExtensionsClientEndpoint extends Endpoint {

        private final CountDownLatch messageLatch;
        private volatile String receivedMessage;

        private MultipleRequestExtensionsClientEndpoint(CountDownLatch messageLatch) {
            this.messageLatch = messageLatch;
        }

        @Override
        public void onOpen(final Session session, EndpointConfig EndpointConfig) {
            try {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                        receivedMessage = message;
                        final List<Extension> negotiatedExtensions = session.getNegotiatedExtensions();
                        if (negotiatedExtensions.size() == 1 && negotiatedExtensions.get(0).getName().equals(MULTIPLE_REQUEST_EXTENSION_NAME)) {
                            messageLatch.countDown();
                        }
                    }
                });

                session.getBasicRemote().sendText(SENT_MESSAGE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String getReceivedMessage() {
            return receivedMessage;
        }
    }
}