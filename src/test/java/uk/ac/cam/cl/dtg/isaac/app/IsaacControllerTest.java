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
package uk.ac.cam.cl.dtg.isaac.app;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import ma.glasnost.orika.MapperFacade;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.powermock.core.classloader.annotations.PowerMockIgnore;

import uk.ac.cam.cl.dtg.isaac.api.IsaacController;
import uk.ac.cam.cl.dtg.isaac.api.managers.GameManager;
import uk.ac.cam.cl.dtg.isaac.api.managers.NoWildcardException;
import uk.ac.cam.cl.dtg.segue.api.SegueApiFacade;
import uk.ac.cam.cl.dtg.segue.api.managers.ContentVersionController;
import uk.ac.cam.cl.dtg.segue.api.managers.StatisticsManager;
import uk.ac.cam.cl.dtg.segue.api.managers.UserManager;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoUserLoggedInException;
import uk.ac.cam.cl.dtg.segue.dao.ILogManager;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.dao.content.ContentManagerException;
import uk.ac.cam.cl.dtg.segue.dto.users.AbstractSegueUserDTO;
import uk.ac.cam.cl.dtg.segue.dto.users.AnonymousUserDTO;
import uk.ac.cam.cl.dtg.util.PropertiesLoader;

/**
 * Test class for the user manager class.
 * 
 */
public class IsaacControllerTest {

	private SegueApiFacade dummyAPI = null;
	private PropertiesLoader dummyPropertiesLoader = null;
	private GameManager dummyGameManager = null;
	private UserManager dummyUserManager = null;
	private String validLiveVersion = "d600d7af95b3cbceecd6910604fa9ea0c5337219";
	private ILogManager dummyLogManager = null;
	private MapperFacade dummyMapper = null;
	private StatisticsManager dummyStatsManager;
	private ContentVersionController contentVersionController;

	/**
	 * Initial configuration of tests.
	 * 
	 * @throws Exception
	 *             - test exception
	 */
	@Before
	public final void setUp() throws Exception {
		this.dummyAPI = createMock(SegueApiFacade.class);
		this.dummyPropertiesLoader = createMock(PropertiesLoader.class);
		this.dummyGameManager = createMock(GameManager.class);
		this.dummyUserManager = createMock(UserManager.class);
		this.dummyLogManager = createMock(ILogManager.class);
		this.dummyStatsManager = createMock(StatisticsManager.class);
		this.contentVersionController = createMock(ContentVersionController.class);
		this.dummyMapper = createMock(MapperFacade.class);
	}

	/**
	 * Verify that when an empty gameboard is noticed a 204 is returned.
	 * 
	 * @throws NoUserLoggedInException
	 * @throws ContentManagerException
	 */
	@Test
	@PowerMockIgnore({ "javax.ws.*" })
	public final void isaacEndPoint_checkEmptyGameboardCausesErrorNoUser_SegueErrorResponseShouldBeReturned()
			throws NoWildcardException, SegueDatabaseException, NoUserLoggedInException,
			ContentManagerException {
		IsaacController isaacController = new IsaacController(dummyAPI, dummyPropertiesLoader,
				dummyGameManager, dummyLogManager, dummyMapper, dummyStatsManager, contentVersionController);

		HttpServletRequest dummyRequest = createMock(HttpServletRequest.class);
		String subjects = "physics";
		String fields = "mechanics";
		String topics = "dynamics";
		String levels = "2,3,4";
		String concepts = "newtoni";

		expect(
				dummyGameManager.generateRandomGameboard(EasyMock.<List<String>> anyObject(),
						EasyMock.<List<String>> anyObject(), EasyMock.<List<String>> anyObject(),
						EasyMock.<List<Integer>> anyObject(), EasyMock.<List<String>> anyObject(),
						EasyMock.<AbstractSegueUserDTO> anyObject())).andReturn(null).atLeastOnce();

		expect(dummyAPI.getCurrentUserIdentifier(dummyRequest)).andReturn(new AnonymousUserDTO("testID"))
				.atLeastOnce();

		replay(dummyGameManager);
		replay(dummyAPI);

		Response r = isaacController.generateTemporaryGameboard(dummyRequest, subjects, fields, topics,
				levels, concepts);

		assertTrue(r.getStatus() == Status.NO_CONTENT.getStatusCode());
		verify(dummyAPI, dummyGameManager);
	}
}
