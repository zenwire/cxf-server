/*
 * Copyright (c) 2013, IT Services, Stockholm University
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of Stockholm University nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package se.su.it.svc;

import org.spocp.client.SPOCPConnection;
import org.spocp.client.SPOCPConnectionFactory;
import org.spocp.client.SPOCPConnectionFactoryImpl;
import org.spocp.client.SPOCPResult;
import se.su.it.svc.annotations.SuCxfSvcSpocpRole;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class SpocpRoleAuthorizor {

  private static SpocpRoleAuthorizor instance = new SpocpRoleAuthorizor();

  private static final int SPOCP_DEFAULT_PORT = 4751;
  private static final String SPOCP_DEFAULT_SERVER = "spocp.su.se";
  private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(SpocpRoleAuthorizor.class);

  private SPOCPConnectionFactoryImpl spocpConnectionFactory = new SPOCPConnectionFactoryImpl();

  public SpocpRoleAuthorizor() {
    spocpConnectionFactory.setPort(SPOCP_DEFAULT_PORT);
    spocpConnectionFactory.setServer(SPOCP_DEFAULT_SERVER);
  }

  public static SpocpRoleAuthorizor getInstance() {
    return instance;
  }

  /**
   *
   * @param uid
   * @param rURI
   * @return
   */
  public final boolean checkRole(String uid, String rURI) {
    boolean ok = false;
    String trueUid = uid.replaceAll("/.*$", "");
    trueUid = trueUid.replaceAll("@.*$", "");

    String theClass = "se.su.it.svc." + rURI.replaceAll("/", "");
    if (theClass.equals("se.su.it.svc.")) {
      return false;
    }//No service, wsdl or status.html on url

    String role = getRole(theClass);

    if (role != null && role.length() > 0) {
      SPOCPConnection spocp = null;
      try {
        spocp = spocpConnectionFactory.getConnection();
        if (spocp != null) {
          String q = "(j2ee-role (identity (uid " + trueUid + ") (realm SU.SE)) (role " + role + "))";
          SPOCPResult res = spocp.query("/", q);
          ok = res.getResultCode() == SPOCPResult.SPOCP_SUCCESS;
        }
      } catch (Exception ex) {
        logger.error("Could not check SPOCP Role: " + role, ex);
      } finally {
        try {
          if (spocp != null) {
            spocp.logout();
          }
        } catch (Exception ignore) {
        }
      }
      try {
        if (spocp != null) {
          spocp.logout();
        }
      } catch (Exception ignore) {
      }
    } else {
      logger.info("No SPOCP Role authentication for: " + theClass + ". Call will be let through.");
      return true;
    }
    return (ok);
  }

  private String getRole(String className) {
    String role = null;
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    try {
      // TODO: Can we do this without reflection? Do we need the other class loader?
      Class serviceClass = classLoader.loadClass(className);
      Class annotationClass = classLoader.loadClass(SuCxfSvcSpocpRole.class.getName());

      Annotation annotation = serviceClass.getAnnotation(annotationClass);
      Method m = annotation.getClass().getMethod("role", null);
      role = (String) m.invoke(annotation, null);
    } catch (Exception e) {
      // Swallow exceptions & return null
      logger.error("Could not figure out class name from request. Faulty classname:" + className, e);
    }

    return role;
  }
}
