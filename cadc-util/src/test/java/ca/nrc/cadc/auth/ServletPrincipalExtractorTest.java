/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2016.                         (c) 2016.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * 4/17/12 - 11:21 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;


import ca.nrc.cadc.util.RSASignatureGeneratorValidatorTest;
import ca.nrc.cadc.util.RsaSignatureGenerator;
import java.io.File;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class ServletPrincipalExtractorTest
{    
    File pubFile, privFile;
    
    @Before
    public void initKeys() throws Exception
    {
        String keysDir = RSASignatureGeneratorValidatorTest.getCompleteKeysDirectoryName();
        RsaSignatureGenerator.genKeyPair(keysDir);
        privFile = new File(keysDir, RsaSignatureGenerator.PRIV_KEY_FILE_NAME);
        pubFile = new File(keysDir, RsaSignatureGenerator.PUB_KEY_FILE_NAME);
    }
    
    @After
    public void cleanupKeys() throws Exception
    {
        pubFile.delete();
        privFile.delete();
    }
    
    @Test
    public void testCookie() throws Exception
    {
        HttpPrincipal principal = new HttpPrincipal("CADCtest");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 2);
        DelegationToken dt = new DelegationToken(principal, null, cal.getTime());
        String cookieValue = DelegationToken.format(dt);
        HttpServletRequest request = createMock(HttpServletRequest.class);
        Cookie cookie = createMock(Cookie.class);
        Cookie[] cookies = {cookie};

        expect(request.getAttribute(
                ServletPrincipalExtractor.CERT_REQUEST_ATTRIBUTE)).andReturn(null);
        expect(request.getHeader(AuthenticationUtil.AUTH_HEADER)).andReturn(null);
        expect(request.getCookies()).andReturn(cookies);
        expect(request.getRemoteUser()).andReturn(null).times(2);
        expect(request.getServerName()).andReturn("cookiedomain").once();
        expect(cookie.getName()).
            andReturn(SSOCookieManager.DEFAULT_SSO_COOKIE_NAME);
        expect(cookie.getValue()).andReturn(cookieValue).atLeastOnce();
        expect(cookie.getDomain()).andReturn("cookiedomain").atLeastOnce();
        replay(request);
        replay(cookie);
        ServletPrincipalExtractor ex = new ServletPrincipalExtractor(request);

        assertEquals(cookieValue, ex.getSSOCookieCredential().getSsoCookieValue());
        assertEquals("cookiedomain", ex.getSSOCookieCredential().getDomain());
        assertTrue(ex.getPrincipals().iterator().next() instanceof HttpPrincipal );
        assertEquals(principal, ex.getPrincipals().iterator().next());
        
        // test expired cookie
        EasyMock.reset(request);
        EasyMock.reset(cookie);
        cal = Calendar.getInstance();

        dt = new DelegationToken(principal, null, cal.getTime());
        cookieValue = DelegationToken.format(dt);
        
        request = createMock(HttpServletRequest.class);
        Cookie[] cookies2 = {cookie};

        expect(request.getAttribute(
                ServletPrincipalExtractor.CERT_REQUEST_ATTRIBUTE)).andReturn(null);
        expect(request.getHeader(AuthenticationUtil.AUTH_HEADER)).andReturn(null);
        expect(request.getCookies()).andReturn(cookies2);
        expect(request.getRemoteUser()).andReturn(null).atLeastOnce();
        expect(cookie.getName()).
            andReturn(SSOCookieManager.DEFAULT_SSO_COOKIE_NAME);
        expect(cookie.getValue()).andReturn(cookieValue).atLeastOnce();
        expect(cookie.getDomain()).andReturn("cookiedomain").atLeastOnce();
        replay(request);
        replay(cookie);
        ex = new ServletPrincipalExtractor(request);

        assertEquals(null, ex.getSSOCookieCredential());
        assertEquals(0, ex.getPrincipals().size() );

    }
}
