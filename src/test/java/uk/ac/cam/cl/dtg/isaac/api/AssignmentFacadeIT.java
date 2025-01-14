/*
 * Copyright 2022 Matthew Trew
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

package uk.ac.cam.cl.dtg.isaac.api;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.ac.cam.cl.dtg.isaac.api.services.AssignmentService;
import uk.ac.cam.cl.dtg.isaac.dto.AssignmentDTO;
import uk.ac.cam.cl.dtg.isaac.dto.AssignmentStatusDTO;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.AdditionalAuthenticationRequiredException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.AuthenticationProviderMappingException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.IncorrectCredentialsProvidedException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.MFARequiredButNotConfiguredException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoCredentialsAvailableException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoUserException;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Date.class, AssignmentFacade.class})
@PowerMockIgnore({"javax.xml.datatype.*", "javax.management.*", "javax.crypto.*", "javax.net.ssl.*", "javax.net.*"})
public class AssignmentFacadeIT extends IsaacIntegrationTest {

    private AssignmentFacade assignmentFacade;

    @Before
    public void setUp() throws Exception {
        // mock the current date/time
        Calendar mockCurrentDateTime = Calendar.getInstance();
        mockCurrentDateTime.set(Calendar.DAY_OF_MONTH, 1);
        mockCurrentDateTime.set(Calendar.MONTH, 6);
        mockCurrentDateTime.set(Calendar.YEAR, 2049);
        PowerMock.expectNew(Date.class).andReturn(mockCurrentDateTime.getTime()).anyTimes();
        PowerMock.replay(Date.class);

        /* because EasyMock says so, we must specify the expected method calls to the mocked badge manager despite this
         being irrelevant to the tests */
        expect(userBadgeManager.updateBadge(anyObject(), anyObject(), anyString())).andReturn(null).anyTimes();
        PowerMock.replay(userBadgeManager);

        // get an instance of the facade to test
        this.assignmentFacade = new AssignmentFacade(assignmentManager, questionManager, userAccountManager,
                groupManager, properties, gameManager, logManager, userAssociationManager, userBadgeManager,
                new AssignmentService(userAccountManager));
    }

    @After
    public void tearDown() throws SQLException {
        // reset the mocks
        PowerMock.reset(Date.class);
        PowerMock.reset(userBadgeManager);

        // reset assignments in DB, so the same assignment can be re-used across tests
        PreparedStatement pst = postgresSqlDb.getDatabaseConnection().prepareStatement(
                "DELETE FROM assignments WHERE gameboard_id in (?,?);");
        pst.setString(1, ITConstants.ASSIGNMENTS_TEST_GAMEBOARD_ID);
        pst.setString(2, ITConstants.ASSIGNMENTS_DATE_TEST_GAMEBOARD_ID);
        pst.executeUpdate();
    }

    @Test
    public void assignBulkEndpoint_setSingleMinimalAndValidAssignmentAsTeacher_assignsSuccessfully() throws
            NoCredentialsAvailableException, NoUserException, SegueDatabaseException,
            AuthenticationProviderMappingException, IncorrectCredentialsProvidedException,
            AdditionalAuthenticationRequiredException, InvalidKeySpecException,
            NoSuchAlgorithmException, MFARequiredButNotConfiguredException {

        // Arrange
        // log in as Teacher, create request
        LoginResult teacherLogin = loginAs(httpSession, ITConstants.TEST_TEACHER_EMAIL,
                ITConstants.TEST_TEACHER_PASSWORD);
        HttpServletRequest assignGameboardsRequest = createRequestWithCookies(new Cookie[]{teacherLogin.cookie});
        replay(assignGameboardsRequest);

        // build assignment
        AssignmentDTO assignment = new AssignmentDTO();
        assignment.setGameboardId(ITConstants.ASSIGNMENTS_TEST_GAMEBOARD_ID);
        assignment.setGroupId(ITConstants.TEST_TEACHERS_AB_GROUP_ID);

        // Act
        // make request
        Response assignBulkResponse = assignmentFacade.assignGameBoards(assignGameboardsRequest,
                Collections.singletonList(assignment));

        // Assert
        // check status code is OK
        assertEquals(Response.Status.OK.getStatusCode(), assignBulkResponse.getStatus());

        // check the assignment assigned successfully
        @SuppressWarnings("unchecked") ArrayList<AssignmentStatusDTO> responseBody =
                (ArrayList<AssignmentStatusDTO>) assignBulkResponse.getEntity();
        assertNull(responseBody.get(0).getErrorMessage());
    }

    @Test
    public void assignBulkEndpoint_setSingleAssignmentWithValidDueDateAsTeacher_assignsSuccessfully() throws
            NoCredentialsAvailableException, NoUserException, SegueDatabaseException,
            AuthenticationProviderMappingException, IncorrectCredentialsProvidedException,
            AdditionalAuthenticationRequiredException, InvalidKeySpecException,
            NoSuchAlgorithmException, MFARequiredButNotConfiguredException {

        // Arrange
        // build due date
        Calendar dueDateCalendar = Calendar.getInstance();
        dueDateCalendar.set(Calendar.DAY_OF_MONTH, 1);
        dueDateCalendar.set(Calendar.MONTH, 1);
        dueDateCalendar.set(Calendar.YEAR, 2050);

        // log in as Teacher, create request
        LoginResult teacherLogin = loginAs(httpSession, ITConstants.TEST_TEACHER_EMAIL,
                ITConstants.TEST_TEACHER_PASSWORD);
        HttpServletRequest assignGameboardsRequest = createRequestWithCookies(new Cookie[]{teacherLogin.cookie});
        replay(assignGameboardsRequest);

        // build assignment
        AssignmentDTO assignment = new AssignmentDTO();
        assignment.setGameboardId(ITConstants.ASSIGNMENTS_TEST_GAMEBOARD_ID);
        assignment.setGroupId(ITConstants.TEST_TEACHERS_AB_GROUP_ID);
        assignment.setDueDate(dueDateCalendar.getTime());

        // Act
        // make request
        Response assignBulkResponse = assignmentFacade.assignGameBoards(assignGameboardsRequest,
                Collections.singletonList(assignment));

        // Assert
        // check status code is OK
        assertEquals(Response.Status.OK.getStatusCode(), assignBulkResponse.getStatus());

        // check the assignment assigned successfully
        @SuppressWarnings("unchecked") ArrayList<AssignmentStatusDTO> responseBody =
                (ArrayList<AssignmentStatusDTO>) assignBulkResponse.getEntity();
        assertNull(responseBody.get(0).getErrorMessage());
    }

    @Test
    public void assignBulkEndpoint_setSingleAssignmentWithDueDateInPastAsTeacher_failsToAssign() throws
            NoCredentialsAvailableException, NoUserException, SegueDatabaseException,
            AuthenticationProviderMappingException, IncorrectCredentialsProvidedException,
            AdditionalAuthenticationRequiredException, InvalidKeySpecException,
            NoSuchAlgorithmException, MFARequiredButNotConfiguredException {

        // Arrange
        // build due date
        Calendar dueDateCalendar = Calendar.getInstance();
        dueDateCalendar.set(Calendar.DAY_OF_MONTH, 1);
        dueDateCalendar.set(Calendar.MONTH, 1);
        dueDateCalendar.set(Calendar.YEAR, 2049);

        // log in as Teacher, create request
        LoginResult teacherLogin = loginAs(httpSession, ITConstants.TEST_TEACHER_EMAIL,
                ITConstants.TEST_TEACHER_PASSWORD);
        HttpServletRequest assignGameboardsRequest = createRequestWithCookies(new Cookie[]{teacherLogin.cookie});
        replay(assignGameboardsRequest);

        // build assignment
        AssignmentDTO assignment = new AssignmentDTO();
        assignment.setGameboardId(ITConstants.ASSIGNMENTS_TEST_GAMEBOARD_ID);
        assignment.setGroupId(ITConstants.TEST_TEACHERS_AB_GROUP_ID);
        assignment.setDueDate(dueDateCalendar.getTime());

        // Act
        // make request
        Response assignBulkResponse = assignmentFacade.assignGameBoards(assignGameboardsRequest,
                Collections.singletonList(assignment));

        // Assert
        // check status code is OK (this is expected even if no assignment assigns successfully)
        assertEquals(Response.Status.OK.getStatusCode(), assignBulkResponse.getStatus());

        // check the assignment failed to assign
        @SuppressWarnings("unchecked") ArrayList<AssignmentStatusDTO> responseBody =
                (ArrayList<AssignmentStatusDTO>) assignBulkResponse.getEntity();
        assertEquals("The assignment cannot be due in the past.", responseBody.get(0).getErrorMessage());
    }

    @Test
    public void assignBulkEndpoint_scheduleSingleAssignmentWithScheduledDateOverAYearInFutureAsTeacher_failsToAssign() throws
            Exception {

        // Arrange
        // build scheduled date
        Calendar scheduledDateCalendar = Calendar.getInstance();
        scheduledDateCalendar.set(Calendar.DAY_OF_MONTH, 2);
        scheduledDateCalendar.set(Calendar.MONTH, 6);
        scheduledDateCalendar.set(Calendar.YEAR, 2050);

        // log in as Teacher, create request
        LoginResult teacherLogin = loginAs(httpSession, ITConstants.TEST_TEACHER_EMAIL,
                ITConstants.TEST_TEACHER_PASSWORD);
        HttpServletRequest assignGameboardsRequest = createRequestWithCookies(new Cookie[]{teacherLogin.cookie});
        replay(assignGameboardsRequest);

        // build assignment
        AssignmentDTO assignment = new AssignmentDTO();
        assignment.setGameboardId(ITConstants.ASSIGNMENTS_DATE_TEST_GAMEBOARD_ID);
        assignment.setGroupId(ITConstants.TEST_TEACHERS_AB_GROUP_ID);
        assignment.setScheduledStartDate(scheduledDateCalendar.getTime());

        // Act
        // make request
        Response assignBulkResponse = assignmentFacade.assignGameBoards(assignGameboardsRequest,
                Collections.singletonList(assignment));

        // Assert
        // check status code is OK (this is expected even if no assignment assigns successfully)
        assertEquals(Response.Status.OK.getStatusCode(), assignBulkResponse.getStatus());

        // check the assignment failed to assign
        @SuppressWarnings("unchecked") ArrayList<AssignmentStatusDTO> responseBody =
                (ArrayList<AssignmentStatusDTO>) assignBulkResponse.getEntity();
        assertEquals("The assignment cannot be scheduled to begin more than one year in the future.",
                responseBody.get(0).getErrorMessage());
    }

    @Test
    public void assignBulkEndpoint_scheduleSingleAssignmentWithScheduledDateAfterDueDateAsTeacher_failsToAssign() throws
            Exception {

        // Arrange
        // build scheduled date
        Calendar scheduledDateCalendar = Calendar.getInstance();
        scheduledDateCalendar.set(Calendar.DAY_OF_MONTH, 5);
        scheduledDateCalendar.set(Calendar.MONTH, 1);
        scheduledDateCalendar.set(Calendar.YEAR, 2050);

        // build due date
        Calendar dueDateCalendar = Calendar.getInstance();
        dueDateCalendar.set(Calendar.DAY_OF_MONTH, 1);
        dueDateCalendar.set(Calendar.MONTH, 1);
        dueDateCalendar.set(Calendar.YEAR, 2050);

        // log in as Teacher, create request
        LoginResult teacherLogin = loginAs(httpSession, ITConstants.TEST_TEACHER_EMAIL,
                ITConstants.TEST_TEACHER_PASSWORD);
        HttpServletRequest assignGameboardsRequest = createRequestWithCookies(new Cookie[]{teacherLogin.cookie});
        replay(assignGameboardsRequest);

        // build assignment
        AssignmentDTO assignment = new AssignmentDTO();
        assignment.setGameboardId(ITConstants.ASSIGNMENTS_DATE_TEST_GAMEBOARD_ID);
        assignment.setGroupId(ITConstants.TEST_TEACHERS_AB_GROUP_ID);
        assignment.setDueDate(dueDateCalendar.getTime());
        assignment.setScheduledStartDate(scheduledDateCalendar.getTime());

        // Act
        // make request
        Response assignBulkResponse = assignmentFacade.assignGameBoards(assignGameboardsRequest,
                Collections.singletonList(assignment));

        // Assert
        // check status code is OK (this is expected even if no assignment assigns successfully)
        assertEquals(Response.Status.OK.getStatusCode(), assignBulkResponse.getStatus());

        // check the assignment failed to assign
        @SuppressWarnings("unchecked") ArrayList<AssignmentStatusDTO> responseBody =
                (ArrayList<AssignmentStatusDTO>) assignBulkResponse.getEntity();
        assertEquals("The assignment cannot be scheduled to begin after it is due.",
                responseBody.get(0).getErrorMessage());
    }
}
