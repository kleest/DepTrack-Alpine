/*
 * This file is part of Alpine.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package alpine.common.util;

import alpine.Config;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProxyUtilTest {

    @Test
    public void fromConfigTest() {
        Assert.assertNull(ProxyUtil.fromConfig(null));
        Assert.assertNull(ProxyUtil.fromConfig(mock(Config.class)));

        var configMock = mock(Config.class);
        when(configMock.getProperty(eq(Config.AlpineKey.HTTP_PROXY_ADDRESS))).thenReturn("proxy.http.example.com");
        when(configMock.getPropertyAsInt(eq(Config.AlpineKey.HTTP_PROXY_PORT))).thenReturn(6666);
        when(configMock.getProperty(eq(Config.AlpineKey.HTTP_PROXY_USERNAME))).thenReturn("domain\\username");
        when(configMock.getPropertyOrFile(eq(Config.AlpineKey.HTTP_PROXY_PASSWORD))).thenReturn("pa$%word");
        when(configMock.getProperty(eq(Config.AlpineKey.NO_PROXY))).thenReturn("acme.com,foo.bar:1234");

        final var proxyCfg = ProxyUtil.fromConfig(configMock);
        Assert.assertNotNull(proxyCfg);
        Assert.assertEquals("proxy.http.example.com", proxyCfg.getHost());
        Assert.assertEquals(6666, proxyCfg.getPort());
        Assert.assertEquals("domain", proxyCfg.getDomain());
        Assert.assertEquals("username", proxyCfg.getUsername());
        Assert.assertEquals("pa$%word", proxyCfg.getPassword());
        Assert.assertEquals(Set.of("acme.com", "foo.bar:1234"), proxyCfg.getNoProxy());
    }

    @Test
    public void fromEnvironmentTest() {
        Assert.assertNull(ProxyUtil.fromEnvironment(null));
        Assert.assertNull(ProxyUtil.fromEnvironment(Collections.emptyMap()));

        final var proxyCfg = ProxyUtil.fromEnvironment(Map.of(
                "https_proxy", "http://proxy.https.example.com:6443",
                "http_proxy", "http://proxy.http.example.com:6666",
                "no_proxy", "acme.com,foo.bar:1234"
        ));
        Assert.assertNotNull(proxyCfg);
        Assert.assertEquals("proxy.https.example.com", proxyCfg.getHost());
        Assert.assertEquals(6443, proxyCfg.getPort());
        Assert.assertNull(proxyCfg.getDomain());
        Assert.assertNull(proxyCfg.getUsername());
        Assert.assertNull(proxyCfg.getPassword());
        Assert.assertEquals(Set.of("acme.com", "foo.bar:1234"), proxyCfg.getNoProxy());
    }

    @Test
    public void fromEnvironmentWithAuthenticationTest() {
        final var proxyCfg = ProxyUtil.fromEnvironment(Map.of(
                "http_proxy", "http://domain%5Cusername:pa$%25word@proxy.http.example.com:6666"
        ));
        Assert.assertNotNull(proxyCfg);
        Assert.assertEquals("proxy.http.example.com", proxyCfg.getHost());
        Assert.assertEquals(6666, proxyCfg.getPort());
        Assert.assertEquals("domain", proxyCfg.getDomain());
        Assert.assertEquals("username", proxyCfg.getUsername());
        Assert.assertEquals("pa$%word", proxyCfg.getPassword());
        Assert.assertNull(proxyCfg.getNoProxy());
    }

}