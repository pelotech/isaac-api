package uk.ac.cam.cl.dtg.segue.auth;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;

import uk.ac.cam.cl.dtg.segue.auth.exceptions.IncorrectCredentialsProvidedException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.InvalidPasswordException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoCredentialsAvailableException;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.NoUserException;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.dao.users.IUserDataManager;
import uk.ac.cam.cl.dtg.segue.dos.users.User;

/**
 * Test class for the SegueLocalAuthenticator class.
 * 
 */
public class SegueLocalAuthenticatorTest {
	
	private IUserDataManager userDataManager;
	
	/**
	 * Initial configuration of tests.
	 * 
	 * @throws Exception
	 *             - test exception
	 */
	@Before
	public final void setUp() throws Exception {
		this.userDataManager = createMock(IUserDataManager.class);
	}

	/**
	 * Verify that setOrChangeUsersPassword fails with bad input.
	 */
	@Test
	public final void segueLocalAuthenticator_setOrChangeUsersPasswordEmptyPassword_exceptionsShouldBeThrown() {
		User someUser = new User();
		someUser.setEmail("test@test.com");
		someUser.setDbId("533ee66842f639e95ce35e29");
		
		replay(userDataManager);
		
		SegueLocalAuthenticator segueAuthenticator = new SegueLocalAuthenticator(this.userDataManager);
		
		try {
			segueAuthenticator.setOrChangeUsersPassword(someUser, null);
			fail("Expected InvalidPasswordException to be thrown as a null password was given.");
		} catch (InvalidPasswordException e) {
			// this is a pass
			
		}
		
		try {
			segueAuthenticator.setOrChangeUsersPassword(someUser, "");
			fail("Expected InvalidPasswordException to be thrown as a empty password was given.");
		} catch (InvalidPasswordException e) {
			// this is a pass
			
		}
	}
	
	/**
	 * Verify that setOrChangeUsersPassword works with correct input and the
	 * result is a user object with base64 encoded passwords and a secure salt.
	 */
	@Test
	public final void segueLocalAuthenticator_setOrChangeUsersPasswordValidPassword_passwordAndHashShouldBePopulatedAsBase64() {
		User someUser = new User();
		someUser.setEmail("test@test.com");
		someUser.setDbId("533ee66842f639e95ce35e29");
		String somePassword = "test5eguePassw0rd";
		replay(userDataManager);
		
		SegueLocalAuthenticator segueAuthenticator = new SegueLocalAuthenticator(this.userDataManager);
		
		try {
			segueAuthenticator.setOrChangeUsersPassword(someUser, somePassword);
			
			// user should now contain an appropriately hashedPassword and salt
			assertTrue(someUser.getPassword() != null);
			assertTrue(someUser.getSecureSalt() != null);
			
			// check that the password and salt are both in base64
			assertTrue(Base64.isBase64(someUser.getPassword()));
			assertTrue(Base64.isBase64(someUser.getSecureSalt()));
			
		} catch (InvalidPasswordException e) {
			fail("This should be a valid password");
		} 
	}
	
	/**
	 * Verify that setOrChangeUsersPassword fails on a bad password.
	 * @throws SegueDatabaseException 
	 * @throws NoCredentialsAvailableException 
	 * @throws NoUserException 
	 */
	@Test
	public final void segueLocalAuthenticator_authenticate_correctEmailAndIncorrectPasswordProvided()
			throws SegueDatabaseException, NoUserException, NoCredentialsAvailableException {
		String someCorrectPasswordPlainText = "test5eguePassw0rd";
		String someCorrectPasswordHashFromDB = "NyACfIYjYUGK7EbtlMAV48+dgyXpa+DPUKHmR1IjY/nAI2xydZUuqtVYc/shQnJ9fhquDOu56C57NGUPsxJ52Q==";
		String someCorrectSecureSaltFromDB = "P77Fhqu2/SAVGDCtu9IkHg==";
		String usersEmailAddress = "test@test.com";
		
		User userFromDatabase = new User();
		userFromDatabase.setDbId("533ee66842f639e95ce35e29");
		userFromDatabase.setEmail(usersEmailAddress);
		userFromDatabase.setPassword(someCorrectPasswordHashFromDB);
		userFromDatabase.setSecureSalt(someCorrectSecureSaltFromDB);
		
		User someUser = new User();
		someUser.setEmail("test@test.com");
		someUser.setDbId("533ee66842f639e95ce35e29");
		String someIncorrectPassword = "password";
		
		expect(userDataManager.getByEmail(usersEmailAddress)).andReturn(userFromDatabase).once();
		
		replay(userDataManager);
		
		SegueLocalAuthenticator segueAuthenticator = new SegueLocalAuthenticator(this.userDataManager);
		try {
			User authenticatedUser = segueAuthenticator.authenticate(usersEmailAddress, someIncorrectPassword);
			fail("This should fail as a bad password has been provided.");
			
		} catch (IncorrectCredentialsProvidedException e) {
			// success
			
		}		
	}
	
	/**
	 * Verify that setOrChangeUsersPassword fails on a bad e-mail and password.
	 * @throws SegueDatabaseException 
	 * @throws IncorrectCredentialsProvidedException 
	 * @throws NoCredentialsAvailableException 
	 * @throws NoUserException 
	 */
	@Test
	public final void segueLocalAuthenticator_authenticate_badEmailAndIncorrectPasswordProvided()
			throws SegueDatabaseException, IncorrectCredentialsProvidedException,
			NoCredentialsAvailableException {
		String someCorrectPasswordPlainText = "test5eguePassw0rd";
		String someCorrectPasswordHashFromDB = "NyACfIYjYUGK7EbtlMAV48+dgyXpa+DPUKHmR1IjY/nAI2xydZUuqtVYc/shQnJ9fhquDOu56C57NGUPsxJ52Q==";
		String someCorrectSecureSaltFromDB = "P77Fhqu2/SAVGDCtu9IkHg==";
		String usersEmailAddress = "test@test.com";
		
		User userFromDatabase = new User();
		userFromDatabase.setDbId("533ee66842f639e95ce35e29");
		userFromDatabase.setEmail(usersEmailAddress);
		userFromDatabase.setPassword(someCorrectPasswordHashFromDB);
		userFromDatabase.setSecureSalt(someCorrectSecureSaltFromDB);
		
		String someBadEmail = "badtest@test.com";
		String someIncorrectPassword = "password";
		
		expect(userDataManager.getByEmail(someBadEmail)).andReturn(null).once();
		
		replay(userDataManager);
		
		SegueLocalAuthenticator segueAuthenticator = new SegueLocalAuthenticator(this.userDataManager);
		try {
			User authenticatedUser = segueAuthenticator.authenticate(someBadEmail, someIncorrectPassword);
			fail("This should fail as a bad email and password has been provided.");
			
		} catch (NoUserException e) {
			// success. This is what we expect.
		} 
	}
	
	/**
	 * Verify that the authenticator creates and authenticates correctly..
	 * @throws SegueDatabaseException 
	 * @throws IncorrectCredentialsProvidedException 
	 * @throws NoCredentialsAvailableException 
	 * @throws InvalidPasswordException 
	 * @throws NoUserException 
	 */
	@Test
	public final void segueLocalAuthenticator_setPasswordAndImmediateAuthenticate_correctEmailAndPasswordProvided()
			throws SegueDatabaseException, IncorrectCredentialsProvidedException,
			NoCredentialsAvailableException, InvalidPasswordException {
		String someCorrectPasswordPlainText = "test5eguePassw0rd";
		String usersEmailAddress = "test@test.com";
		
		User userFromDatabase = new User();
		userFromDatabase.setDbId("533ee66842f639e95ce35e29");
		userFromDatabase.setEmail(usersEmailAddress);
				
		expect(userDataManager.getByEmail(usersEmailAddress)).andReturn(userFromDatabase).once();
		
		replay(userDataManager);
		
		SegueLocalAuthenticator segueAuthenticator = new SegueLocalAuthenticator(this.userDataManager);
		try {
			// first try and mutate the user object using the the set method.
			// this should set the password and secure hash on the user object.
			segueAuthenticator.setOrChangeUsersPassword(userFromDatabase, someCorrectPasswordPlainText);
			
			// now try and authenticate using the password we just created.
			User authenticatedUser = segueAuthenticator.authenticate(usersEmailAddress, someCorrectPasswordPlainText);
			
			assertTrue(authenticatedUser.getPassword().equals(userFromDatabase.getPassword()));
		} catch (NoUserException e) {
			fail("We expect a user to be returned");
		} 
	}	
}