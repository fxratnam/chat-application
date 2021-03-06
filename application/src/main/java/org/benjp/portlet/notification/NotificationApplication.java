/*
 * Copyright (C) 2012 eXo Platform SAS.
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

package org.benjp.portlet.notification;

import juzu.*;
import juzu.plugin.ajax.Ajax;
import juzu.request.HttpContext;
import juzu.request.RenderContext;
import juzu.template.Template;
import org.benjp.listener.ServerBootstrap;
import org.benjp.model.SpaceBean;
import org.benjp.utils.PropertyManager;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SessionScoped
public class NotificationApplication
{

  @Inject
  @Path("index.gtmpl")
  Template index;

  String token_ = null;
  String remoteUser_ = null;
  boolean profileInitialized_ = false;

  OrganizationService organizationService_;

  SpaceService spaceService_;

  @Inject
  public NotificationApplication(OrganizationService organizationService, SpaceService spaceService)
  {
    organizationService_ = organizationService;
    spaceService_ = spaceService;
  }

  @View
  public void index(RenderContext renderContext) throws IOException
  {
    String chatServerURL = PropertyManager.getProperty(PropertyManager.PROPERTY_CHAT_SERVER_URL);
    String chatPage = PropertyManager.getProperty(PropertyManager.PROPERTY_CHAT_PORTAL_PAGE);
    remoteUser_ = renderContext.getSecurityContext().getRemoteUser();
    token_ = ServerBootstrap.getTokenService().getToken(remoteUser_);
    String chatIntervalStatus = PropertyManager.getProperty(PropertyManager.PROPERTY_INTERVAL_STATUS);
    String chatIntervalNotif = PropertyManager.getProperty(PropertyManager.PROPERTY_INTERVAL_NOTIF);

    index.with().set("user", remoteUser_).set("token", token_)
            .set("chatServerURL", chatServerURL).set("chatPage", chatPage)
            .set("chatIntervalStatus", chatIntervalStatus)
            .set("chatIntervalNotif", chatIntervalNotif)
            .render();
  }

  @Ajax
  @Resource
  public Response.Content initUserProfile()
  {
    String  out = "nothing to update";
    if (!profileInitialized_)
    {
      try
      {

        // Add User in the DB
        addUser(remoteUser_, token_);

        // Set user's Full Name in the DB
        saveFullNameAndEmail(remoteUser_);

        // Set user's Spaces in the DB
        saveSpaces(remoteUser_);

        out = "updated";

        profileInitialized_ = true;
      }
      catch (Exception e)
      {
        profileInitialized_ = false;
        return Response.notFound("Error during init, try later");
      }
    }

    return Response.ok(out).withMimeType("text/html; charset=UTF-8").withHeader("Cache-Control", "no-cache");

  }


/*
  private String getSessionId(HttpContext httpContext)
  {
    for (Cookie cookie:httpContext.getCookies())
    {
      if("JSESSIONID".equals(cookie.getName()))
      {
        return cookie.getValue();
      }
    }
    return null;

  }
*/

  protected void addUser(String remoteUser, String token)
  {
    ServerBootstrap.getTokenService().addUser(remoteUser, token);
  }

  protected String saveFullNameAndEmail(String username)
  {
    String fullname = username;
    try
    {

      fullname = ServerBootstrap.getUserService().getUserFullName(username);
      if (fullname==null)
      {
        User user = organizationService_.getUserHandler().findUserByName(username);
        fullname = user.getFirstName()+" "+user.getLastName();
        ServerBootstrap.getUserService().addUserFullName(username, fullname);
        ServerBootstrap.getUserService().addUserEmail(username, user.getEmail());
      }


    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    return fullname;
  }

  protected void saveSpaces(String username)
  {
    try
    {
      ListAccess<Space> spacesListAccess = spaceService_.getAccessibleSpacesWithListAccess(username);
      List<Space> spaces = Arrays.asList(spacesListAccess.load(0, spacesListAccess.getSize()));
      List<SpaceBean> beans = new ArrayList<SpaceBean>();
      for (Space space:spaces)
      {
        SpaceBean spaceBean = new SpaceBean();
        spaceBean.setDisplayName(space.getDisplayName());
        spaceBean.setGroupId(space.getGroupId());
        spaceBean.setId(space.getId());
        spaceBean.setShortName(space.getShortName());
        beans.add(spaceBean);
      }
      ServerBootstrap.getUserService().setSpaces(username, beans);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }

  }

}
