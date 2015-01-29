/**
 * Copyright 2014 Stephen Cummins
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.cam.cl.dtg.segue.api;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import uk.ac.cam.cl.dtg.segue.api.managers.GroupManager;
import uk.ac.cam.cl.dtg.segue.api.managers.UserManager;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoUserException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoUserLoggedInException;
import uk.ac.cam.cl.dtg.segue.dao.ILogManager;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.dos.UserGroupDO;
import uk.ac.cam.cl.dtg.segue.dto.SegueErrorResponse;
import uk.ac.cam.cl.dtg.segue.dto.users.RegisteredUserDTO;
import uk.ac.cam.cl.dtg.util.PropertiesLoader;

/**
 * GroupsFacade.
 * This is responsible for exposing group management functionality.
 * 
 * @author Stephen Cummins
 */
@Path("/groups")
public class GroupsFacade extends AbstractSegueFacade {
	private final UserManager userManager;

	private static final Logger log = LoggerFactory.getLogger(GroupsFacade.class);
	private final GroupManager groupManager;

	/**
	 * Create an instance of the authentication Facade.
	 * 
	 * @param properties
	 *            - properties loader for the application
	 * @param userManager
	 *            - user manager for the application
	 * @param logManager
	 *            - so we can log interesting events.
	 * @param groupManager
	 *            - so that we can manage groups.
	 */
	@Inject
	public GroupsFacade(final PropertiesLoader properties, final UserManager userManager,
			final ILogManager logManager, final GroupManager groupManager) {
		super(properties, logManager);
		this.userManager = userManager;
		this.groupManager = groupManager;
	}

	/**
	 * Get all groups owned by the current user.
	 * 
	 * @param request
	 *            - so we can identify the current user.
	 * @return List of user associations.
	 */
	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getGroupsForCurrentUser(@Context final HttpServletRequest request) {
		try {
			RegisteredUserDTO user = userManager.getCurrentRegisteredUser(request);

			return Response.ok(
					groupManager.getGroupsByOwner(user.getDbId()))
					.build();
		} catch (NoUserLoggedInException e) {
			return SegueErrorResponse.getNotLoggedInResponse();
		}
	}

	/**
	 * Function to allow users to create a group.
	 * This function requires the group DTO to be provided.
	 * 
	 * @param request
	 *            - so we can find out who the current user is
	 * @param groupDTO
	 *            - Containing required information to create a group
	 * @return a Response containing the group
	 */
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createGroup(@Context final HttpServletRequest request,
			final UserGroupDO groupDTO) {
		if (null == groupDTO.getGroupName() || groupDTO.getGroupName().isEmpty()) {
			return new SegueErrorResponse(Status.BAD_REQUEST, "Group name must be specified.").toResponse();
		}

		try {
			RegisteredUserDTO user = userManager.getCurrentRegisteredUser(request);
			UserGroupDO group = groupManager.createUserGroup(groupDTO.getGroupName(), user);

			return Response.ok(group).build();
		} catch (SegueDatabaseException e) {
			log.error("Database error while trying to create user group. ", e);
			return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Database error", e).toResponse();
		} catch (NoUserLoggedInException e) {
			return SegueErrorResponse.getNotLoggedInResponse();
		} 
	}
	
	/**
	 * List all group membership.
	 * @param request - for authentication
	 * @param groupId - to look up info for a group.
	 * @return List of registered users.
	 */
	@GET
	@Path("{group_id}/membership/")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUsersInGroup(
			@Context final HttpServletRequest request,
			@PathParam("group_id") final String groupId) {
		if (null == groupId || groupId.isEmpty()) {
			return new SegueErrorResponse(Status.BAD_REQUEST, "Group name must be specified.").toResponse();
		}

		try {
			RegisteredUserDTO user = userManager.getCurrentRegisteredUser(request);
			
			UserGroupDO group = groupManager.getGroupById(groupId);
			
			if (!group.getOwnerId().equals(user.getDbId())) {
				return new SegueErrorResponse(Status.FORBIDDEN, "You are not the owner of this group").toResponse();
			}
			
			List<RegisteredUserDTO> members = groupManager.getUsersInGroup(group);

			return Response.ok(members).build();
		} catch (SegueDatabaseException e) {

			return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Database error", e).toResponse();
		} catch (NoUserLoggedInException e) {
			return SegueErrorResponse.getNotLoggedInResponse();
		} 
	}
	
	/**
	 * @param request - for authentication
	 * @param groupId - group of interest.
	 * @param userId - user to add.
	 * @return 200 or error response
	 */
	@POST
	@Path("{group_id}/membership/{user_id}")
	public Response addUserToGroup(@Context final HttpServletRequest request,
			@PathParam("group_id") final String groupId, 
			@PathParam("user_id") final String userId) {
		if (null == groupId || groupId.isEmpty()) {
			return new SegueErrorResponse(Status.BAD_REQUEST, "Group name must be specified.").toResponse();
		}

		try {
			// ensure there is a user currently logged in.
			userManager.getCurrentRegisteredUser(request);
			
			UserGroupDO groupBasedOnId = groupManager.getGroupById(groupId);
			
			RegisteredUserDTO userToAdd = userManager.getUserDTOById(userId);
			
			groupManager.addUserToGroup(groupBasedOnId, userToAdd);

			return Response.ok().build();
		} catch (SegueDatabaseException e) {
			log.error("Database error while trying to add user to a group. ", e);
			return new SegueErrorResponse(Status.INTERNAL_SERVER_ERROR, "Database error", e).toResponse();
		} catch (NoUserLoggedInException e) {
			return SegueErrorResponse.getNotLoggedInResponse();
		} catch (NoUserException e) {
			return new SegueErrorResponse(Status.BAD_REQUEST, "User specified does not exist.").toResponse();
		} 
	}
}