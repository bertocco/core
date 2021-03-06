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
 * 12/8/11 - 2:39 PM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.auth;

import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.Test;

import ca.nrc.cadc.util.RSASignatureGeneratorValidatorTest;
import ca.nrc.cadc.util.RsaSignatureGenerator;
import java.io.File;
import java.io.FileWriter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;

public class DelegationTokenTest
{
    private static final Logger log = Logger.getLogger(DelegationTokenTest.class);
    
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.auth", Level.INFO);
        Log4jInit.setLevel("ca.nrc.cadc.util", Level.INFO);
    }
    
    public static class TestScopeValidator extends DelegationToken.ScopeValidator
    {

        @Override
        public void verifyScope(URI scope, String requestURI) 
            throws InvalidDelegationTokenException 
        {
            if ("foo:bar".equals(scope.toASCIIString()) )
                return; // OK
            super.verifyScope(scope, requestURI); // test default behaviour indirectly
        }
        
    }

    File pubFile, privFile;
    
    @Before
    public void initKeys() throws Exception
    {
        File config = FileUtil.getFileFromResource("DelegationToken.properties", DelegationTokenTest.class);
        File keysDir = config.getParentFile();
        //String keysDir = RSASignatureGeneratorValidatorTest.getCompleteKeysDirectoryName();
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
    public void matches() throws Exception
    {
        // configure the test scope validator
        File config = FileUtil.getFileFromResource("DelegationToken.properties", DelegationTokenTest.class);
        FileWriter w = new FileWriter(config);
        w.write(DelegationToken.class.getName() + ".scopeValidator = " + TestScopeValidator.class.getName());
        w.write("\n");
        w.flush();
        w.close();
        
        // round trip test with signature
       
        HttpPrincipal userid = new HttpPrincipal("someuser");
        URI scope = new URI("foo:bar");
        int duration = 10; // h
        Calendar expiry = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        expiry.add(Calendar.HOUR, duration);
        DelegationToken expToken = new DelegationToken(userid, scope, expiry.getTime());
        
        String token = DelegationToken.format(expToken);
        log.debug("valid token: " + token);
        DelegationToken actToken = DelegationToken.parse(token, null);
        
        assertEquals("User id not the same", expToken.getUser(),
                actToken.getUser());
        assertEquals("Expiry time not the same", expToken.getExpiryTime(),
                actToken.getExpiryTime());
        assertEquals("Scope not the same", expToken.getScope(),
                actToken.getScope());
        
        
        // round trip test without signature and principal with proxy user
        
        userid = new HttpPrincipal("someuser", "someproxyuser");
        scope = new URI("foo:bar");
        duration = 10; // h
        expiry = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        expiry.add(Calendar.HOUR, duration);
        expToken = new DelegationToken(userid, scope, expiry.getTime());

        token = DelegationToken.format(expToken);
        log.debug("valid token: " + token);
        actToken = DelegationToken.parse(token, null);
        
        assertEquals("User id not the same", expToken.getUser(),
                actToken.getUser());
        assertEquals("Expiry time not the same", expToken.getExpiryTime(),
                actToken.getExpiryTime());
        assertEquals("Scope not the same", expToken.getScope(),
                actToken.getScope());
        
        // invalid scope
        try
        {
            DelegationToken wrongScope = new DelegationToken(userid, new URI("bar:baz"), expiry.getTime());
            DelegationToken.parse(DelegationToken.format(wrongScope), null);
            fail("Exception expected");
        }
        catch(InvalidDelegationTokenException expected) 
        {
            log.debug("caught expected exception: " + expected);
        }
        
        Calendar expiredDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        expiredDate.add(Calendar.DATE, -1);
        // parse expired token
        try
        {
            expToken = new DelegationToken(userid, scope, expiredDate.getTime());
            DelegationToken.parse(DelegationToken.format(expToken), null);
            fail("Exception expected");
        }
        catch(InvalidDelegationTokenException expected) 
        {
            log.debug("caught expected exception: " + expected);
        }
        
        //invalidate signature field
        try
        {
            expToken = new DelegationToken(userid, scope, expiry.getTime());
            
            token = DelegationToken.format(expToken);
            CharSequence subSequence = token.subSequence(0,  token.indexOf("signature") + 10);
            DelegationToken.parse(subSequence.toString(), null);
            fail("Exception expected");
        }
        catch(InvalidDelegationTokenException expected) 
        {
            log.debug("caught expected exception: " + expected);
        }
        
        
        // tamper with one character in the signature
        try
        {
            expToken = new DelegationToken(userid, scope, expiry.getTime());
            
            token = DelegationToken.format(expToken);
            char toReplace = token.charAt(token.indexOf("signature") + 10);
            if (toReplace != 'A')
            {
                token = token.replace(token.charAt(token.indexOf("signature") + 10), 'A');
            }
            else
            {
                token = token.replace(token.charAt(token.indexOf("signature") + 10), 'B');
            }
            DelegationToken.parse(token, null);
            fail("Exception expected");
        }
        catch(InvalidDelegationTokenException expected) 
        {
            log.debug("caught expected exception: " + expected);
        }
    }
}

