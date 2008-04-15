package org.mobicents.servlet.sip.router;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.sip.SipApplicationRouter;
import javax.servlet.sip.SipApplicationRouterInfo;
import javax.servlet.sip.SipApplicationRoutingDirective;
import javax.servlet.sip.SipApplicationRoutingRegion;
import javax.servlet.sip.SipServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implementation of the default application router as defined per JSR 289 Appendix C
 * 
 * <p>
 * As an application router component is essential for the functioning of the
 * container the following application router logic SHOULD be available with
 * every container compliant with this specification. The container
 * implementations MAY chose to provide a much richer application router
 * component. For the purpose of this discussion the application router defined
 * in this appendix is termed as Default Application Router (DAR).
 * </p>
 * 
 * <p>
 * The Application Router and the Container have a simple contract defined by
 * the <code>SipApplicationRouter</code> interface. <br />
 * The DAR MUST implement all the methods of that interface as described in this
 * document.
 * <p>
 * <b>The DAR Configuration File</b><br />
 * 
 * The DAR works off a simple configuration text file which is modeled as a Java
 * properties file:
 * 
 * <ul>
 * <li>The properties file MUST be made available to the DAR and the
 * location/content of this file MUST be accessible from a hierarchical URI
 * which itself is to be supplied as a system property "javax.servlet.sip.dar"
 * </li>
 * <li>The propeties file has a simple format in which the name of the property
 * is the SIP method and the value is a simple comma separated stringified value
 * for the SipRouterInfo object.
 * 
 * <pre>
 *       INVITE: (sip-router-info-1), (sip-router-info-2)..
 *       SUBSCRIBE:  (sip-router-info-3), (sip-router-info-4)..
 * </pre>
 * 
 * </li>
 * <li> The properties file is first read by the DAR when the
 * <code>init()</code> is first called on the DAR. The arguments passed in the
 * <code>init()</code> are ignored. </li>
 * 
 * <li> The properties file is refreshed each time
 * <code>applicationDeployed()</code> or <code>applicationUndeployed()</code>
 * is called, similar to init the argument of these two invocations are ignored,
 * these callbacks act just as a trigger to read the file afresh. </li>
 * </ul>
 * </p>
 * <p>
 * The sip-router-info data that goes in the properties file is a stringified
 * version of the SipRouterInfo object. It consists of the following information :
 * <ul>
 * <li>The name of the application as known to the container</li>
 * <li>The identity of the subscriber that the DAR returns. It can return any
 * header in the SIP request using the DAR directive DAR:<i>SIP_HEADER</i> e.g
 * "DAR:From" would return the sip uri in From header. Or alternatively it can
 * return any string.</li>
 * 
 * <li>The routing region, one of the strings "ORIGINATING", "TERMINATING" or
 * "NEUTRAL"</li>
 * <li>A SIP URI indicating the route as returned by the application route, it
 * can be an empty string. </li>
 * <li>A route modifier which can be any one of the strings "ROUTE",
 * "NO_ROUTE", or "CLEAR_ROUTE" </li>
 * <li>A string representing stateInfo. As stateInfo is for application
 * router's internal use only, what goes in this is up to the individual DAR
 * implementations. As a hint the stateInfo could contain the index into the
 * list of sip-router-info that was returned last. </li>
 * </ul>
 * 
 * </p>
 * <p>
 * Following is an example of the DAR configuration file:
 * 
 * <pre>
 *  INVITE: (&quot;OriginatingCallWaiting&quot;, &quot;DAR:From&quot;, &quot;ORIGINATING&quot;, &quot;&quot;,
 *  &quot;NO_ROUTE&quot;, &quot;0&quot;), (&quot;CallForwarding&quot;, &quot;DAR:To&quot;, &quot;TERMINATING&quot;, &quot;&quot;,
 *  NO_ROUTE&quot;, &quot;1&quot;)
 * </pre>
 * 
 * In this example the DAR is setup to invoke two applications on INVITE request
 * one each in the originating and the terminating half. The applications are
 * identified by their names as defined in the application deployment
 * descriptors and used here. The subscriber identity returned in this case is
 * the URI from the From and To header respectively for the two applications.
 * The DAR does not return any route to the container and maintains the
 * invocation state in the stateInfo as the index of the last application in the
 * list.
 * 
 * <p>
 * <b>The DAR Operation</b><br />
 * The key interaction point between the Container and the Application Router is
 * the method
 * 
 * <pre>
 * SipApplicationRouterInfo getNextApplication(SipServletRequest initialRequest,
 * 		SipApplicationRoutingRegion region,
 * 		SipApplicationRoutingDirective directive, java.lang.String stateInfo);
 * </pre>
 * 
 * This method is invoked when an initial request is received by the container.
 * When this method is invoked on DAR it will make use of the stateInfo and the
 * initialRequest parameters and find out what SIP method is in the request.
 * Next it will create the object <code>
 * SipApplicationRouterInfo</code> from
 * the sip-router-info information in the properties file, starting from the
 * first in the list. The stateInfo could contain the index of the last
 * sip-router-info returned so on next invocation of getNextApplication the DAR
 * proceeds to the next sip-router- info in the list. The order of declaration
 * of sip-router-info becomes the priority order of invocation.
 * 
 * <p>
 * As you would notice this is a minimalist application router with no
 * processing logic besides the declaration of the application order. It is
 * expected that in the real world deployments the application router shall play
 * an extremely important role in application orchestration and composition and
 * shall make use of complex rules and diverse data repositories. The DAR is
 * specified as being a very simple implementation that shall be available as
 * part of the reference implementation of this specification and can be used in
 * place of an application router.
 * 
 * </p>
 */
public class DefaultApplicationRouter implements SipApplicationRouter {	
	//	the logger
	private static Log log = LogFactory.getLog(DefaultApplicationRouter.class);
	//the parser for the properties file
	private DefaultApplicationRouterParser defaultApplicationRouterParser;
	//Applications deployed within the container
	List<String> containerDeployedApplicationNames = null;
	//List of applications defined in the defautl application router properties file
	Map<String, List<SipApplicationRouterInfo>> sipApplicationRouterInfos;
	
	/**
	 * Default Constructor
	 */
	public DefaultApplicationRouter() {
		containerDeployedApplicationNames = new ArrayList<String>();
		defaultApplicationRouterParser = new DefaultApplicationRouterParser();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void applicationDeployed(List<String> newlyDeployedApplicationNames) {		
		synchronized (containerDeployedApplicationNames) {
			containerDeployedApplicationNames.addAll(newlyDeployedApplicationNames);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void applicationUndeployed(List<String> undeployedApplicationNames) {
		synchronized (containerDeployedApplicationNames) {
			containerDeployedApplicationNames.removeAll(undeployedApplicationNames);
		}

	}

	/**
	 * {@inheritDoc}
	 */
	public void destroy() {
		synchronized (containerDeployedApplicationNames) {
			containerDeployedApplicationNames.clear();			
		}

	}

	/**
	 * {@inheritDoc}
	 */
	public SipApplicationRouterInfo getNextApplication(
			SipServletRequest initialRequest,
			SipApplicationRoutingRegion region,
			SipApplicationRoutingDirective directive, Serializable stateInfo) {
		// TODO implements the real matching logic for now it's gonna be first app is the one always interested :-)
		List<SipApplicationRouterInfo> sipApplicationRouterInfoList = 
			sipApplicationRouterInfos.get(initialRequest.getMethod());
		if(sipApplicationRouterInfoList != null || sipApplicationRouterInfoList.size() > 0) {
			return sipApplicationRouterInfoList.get(0);
		}	
		return null;
	}

	/**
	 * load the configuration file as defined in appendix C of JSR289
	 */
	public void init() {		
		defaultApplicationRouterParser.init();		
		try {
			sipApplicationRouterInfos = defaultApplicationRouterParser.parse();
		} catch (ParseException e) {
			log.fatal("Impossible to parse the default application router configuration file",e);
			throw new IllegalArgumentException("Impossible to parse the default application router configuration file",e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void init(List<String> deployedApplicationNames) {
		init();
		this.containerDeployedApplicationNames.addAll(deployedApplicationNames);
	}

}
