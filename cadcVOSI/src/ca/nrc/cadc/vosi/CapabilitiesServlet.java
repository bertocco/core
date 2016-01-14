package ca.nrc.cadc.vosi;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.log.ServletLogInfo;
import ca.nrc.cadc.log.WebServiceLogInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;


import ca.nrc.cadc.vosi.util.Util;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import javax.security.auth.Subject;
import javax.servlet.ServletConfig;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

/**
 * Servlet implementation class CapabilityServlet
 */
public class CapabilitiesServlet extends HttpServlet
{
    private static Logger log = Logger.getLogger(CapabilitiesServlet.class);
    private static final long serialVersionUID = 201003131300L;
    
    private String staticCapabilities;
    private String extensionSchemaNS;
    private String extensionSchemaLocation;
       
    @Override
    public void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);

        String str = config.getInitParameter("input");
        if (str != null)
        {
            log.info("static capabilities: " + str);
            try
            {
                // validate 
                URL resURL = config.getServletContext().getResource(str);
                CapabilitiesParser cp = getParser();
                Document doc = cp.parse(resURL.openStream());
                StringWriter sw = new StringWriter();
                XMLOutputter out = new XMLOutputter();
                out.output(doc, sw);
                this.staticCapabilities = sw.toString();
            }
            catch(Throwable t)
            {
                log.error("CONFIGURATION ERROR: failed to read static capabilities file: " + str, t);
            }
        }
    }
    
    protected CapabilitiesParser getParser()
    {
        return new CapabilitiesParser(true);
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        
        boolean started = false;
        
        WebServiceLogInfo logInfo = new ServletLogInfo(request);
        long start = System.currentTimeMillis();
        
        try
        {
            Subject subject = AuthenticationUtil.getSubject(request);
            logInfo.setSubject(subject);
            log.info(logInfo.start());
            
            if (staticCapabilities != null)
                transformCap(request, response);
            else
                generateCap(request, response);
        }
        catch(Throwable t)
        {
            logInfo.setSuccess(false);
            logInfo.setMessage(t.toString());
            log.error("BUG: failed to rewrite hostname in accessURL elements", t);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.getMessage());
        }
        finally
        {
            logInfo.setElapsedTime(System.currentTimeMillis() - start);
            log.info(logInfo.end());
        }
    }
	
    private void transformCap(HttpServletRequest request, HttpServletResponse response)
        throws IOException, JDOMException
    {
        URL rurl = new URL(request.getRequestURL().toString());
        String hostname = rurl.getHost();
        StringReader sr = new StringReader(staticCapabilities);
        CapabilitiesParser cp = new CapabilitiesParser(false);
        if (extensionSchemaNS != null && extensionSchemaLocation  != null)
            cp.addSchemaLocation(extensionSchemaNS, extensionSchemaLocation);
        Document doc = cp.parse(sr);

        Element root = doc.getRootElement();
        List<Namespace> nsList = new ArrayList<Namespace>();
        nsList.addAll(root.getAdditionalNamespaces());
        nsList.add(root.getNamespace());

        String xpath = "/vosi:capabilities/capability/interface/accessURL";
        XPathFactory xf = XPathFactory.instance();
        XPathExpression<Element> xp = xf.compile(xpath, Filters.element(),
                null, nsList);
        List<Element> accessURLs = xp.evaluate(doc);
        log.debug("xpath[" + xpath + "] found: " + accessURLs.size());
        for (Element e : accessURLs)
        {
            String surl = e.getTextTrim();
            log.debug("accessURL: " + surl);
            URL url = new URL(surl);
            URL nurl = new URL(url.getProtocol(), hostname, url.getPath());
            log.debug("accessURL: " + surl + " -> " + nurl);
            e.setText(nurl.toExternalForm());
        }
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        response.setContentType("text/xml");
        out.output(doc, response.getOutputStream());
    }
    
    private void generateCap(HttpServletRequest request, HttpServletResponse response)
        throws IOException
    {
        List<Capability> caps = new ArrayList<Capability>();
        String hostContext = Util.getStringPartBefore(request.getRequestURL().toString(), "/capabilities");

        String space = " ";
        String resourceName, paramValue, standardID, role;
        Capability capability;
        Enumeration enumParam = this.getInitParameterNames();
        while (enumParam.hasMoreElements())
        {
            resourceName = (String) enumParam.nextElement();
            paramValue = this.getInitParameter(resourceName);
            standardID = Util.getStringPartBefore(paramValue, space);
            role = Util.getStringPartAfter(paramValue, space);
            capability = new Capability(hostContext, standardID, resourceName, role);
            caps.add(capability);
        }

        Capabilities capabilities = new Capabilities(caps);
        Document document = capabilities.toXmlDocument();
        XMLOutputter xop = new XMLOutputter(Format.getPrettyFormat());
        response.setContentType("text/xml");
        xop.output(document, response.getOutputStream());
    }
}
