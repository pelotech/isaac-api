package uk.ac.cam.cl.dtg.segue.dao.users;

import java.util.List;

import org.apache.commons.lang3.Validate;
import org.mongojack.DBCursor;
import org.mongojack.DBQuery;
import org.mongojack.DBUpdate;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.mongojack.internal.MongoJackModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.Lists;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoException;

import uk.ac.cam.cl.dtg.segue.api.Constants;
import uk.ac.cam.cl.dtg.segue.auth.AuthenticationProvider;
import uk.ac.cam.cl.dtg.segue.auth.exceptions.DuplicateAccountException;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.dao.content.ContentMapper;
import uk.ac.cam.cl.dtg.segue.dos.QuestionAttemptUserRecord;
import uk.ac.cam.cl.dtg.segue.dos.users.LinkedAccount;
import uk.ac.cam.cl.dtg.segue.dos.users.User;
import uk.ac.cam.cl.dtg.segue.dto.QuestionValidationResponseDTO;

/**
 * This class is responsible for managing and persisting user data.
 * 
 * @author Stephen Cummins
 */
public class MongoUserDataManager implements IUserDataManager {

	private static final Logger log = LoggerFactory
			.getLogger(MongoUserDataManager.class);

	private final DB database;
	private final ContentMapper contentMapper;
	
	private static final String USER_COLLECTION_NAME = "users";
	private static final String LINKED_ACCOUNT_COLLECTION_NAME = "linkedAccounts";
	private static final String QUESTION_ATTEMPTS_COLLECTION_NAME = "questionAttempts";

	/**
	 * Creates a new user data maanger object.
	 * 
	 * @param database
	 *            - the database reference used for persistence.
	 * @param contentMapper - The preconfigured content mapper for pojo mapping.
	 */
	@Inject
	public MongoUserDataManager(final DB database, final ContentMapper contentMapper) {
		this.database = database;
		this.contentMapper = contentMapper;
		initialiseDataManager();
	}

	@Override
	public final User createOrUpdateUser(final User user) throws SegueDatabaseException {
		JacksonDBCollection<User, String> jc = JacksonDBCollection.wrap(
				database.getCollection(USER_COLLECTION_NAME), User.class,
				String.class);

		try {
			WriteResult<User, String> r = jc.save(user);

			if (r.getError() != null) {
				log.error("Error during database update " + r.getError());
				throw new SegueDatabaseException(
						"MongoDB encountered an exception while creating a new user account: " + r.getError());
			}
			
			return r.getSavedObject();	
		} catch (MongoException.DuplicateKey e) {
			throw new DuplicateAccountException("A user with a duplicate key exists in the database.", e);
		} catch (MongoException e) {
			String errorMessage = "MongoDB encountered an exception while attempting to create a user account.";
			log.error(errorMessage, e);
			throw new SegueDatabaseException(errorMessage, e);
		}
	}
	
	@Override
	public final String registerNewUserWithProvider(final User user, final AuthenticationProvider provider,
			final String providerUserId) throws SegueDatabaseException {
		Validate.notNull(user);
		Validate.notNull(provider);
		Validate.notNull(providerUserId);

		// create the users local account.
		User localUser = this.createOrUpdateUser(user);
		String localUserId = localUser.getDbId().toString();
		
		// link the provider account to the newly created account.
		this.linkAuthProviderToAccount(localUser, provider, providerUserId);

		return localUserId;
	}

	@Override
	public final User getById(final String id) throws SegueDatabaseException {
		if (null == id) {
			return null;
		}

		// Since we are attaching our own auto mapper we have to do MongoJack configure on it. 
		ObjectMapper objectMapper = contentMapper.getContentObjectMapper();
		MongoJackModule.configure(objectMapper);
				
		JacksonDBCollection<User, String> jc = JacksonDBCollection.wrap(
				database.getCollection(USER_COLLECTION_NAME), User.class,
				String.class, objectMapper);
		try {
			// Do database query using plain mongodb so we only have to read from
			// the database once.
			User user = jc.findOneById(id);
			
			return user;
			
		} catch (MongoException e) {
			String errorMessage = "MongoDB encountered an exception while attempting to find a user account by id.";
			log.error(errorMessage, e);
			throw new SegueDatabaseException(errorMessage, e);
		}
	}

	@Override
	public User getByEmail(final String email) throws SegueDatabaseException {
		if (null == email) {
			return null;
		}

		JacksonDBCollection<User, String> jc = JacksonDBCollection.wrap(
				database.getCollection(USER_COLLECTION_NAME), User.class,
				String.class);
		try {
			// Do database query using plain mongodb so we only have to read from
			// the database once.
			User user = jc.findOne(new BasicDBObject(
					Constants.LOCAL_AUTH_EMAIL_FIELDNAME, email.trim()));

			return user;
		} catch (MongoException e) {
			String errorMessage = "MongoDB encountered an exception "
					+ "while attempting to find a user account by email address.";
			log.error(errorMessage, e);
			throw new SegueDatabaseException(errorMessage, e);
		}
	}

	@Override
	public User getByResetToken(final String token) throws SegueDatabaseException {
		if (null == token) {
			return null;
		}

		JacksonDBCollection<User, String> jc = JacksonDBCollection.wrap(
				database.getCollection(USER_COLLECTION_NAME), User.class,
				String.class);
		try {
			// Do database query using plain mongodb so we only have to read from
			// the database once.
			User user = jc.findOne(new BasicDBObject(
					Constants.LOCAL_AUTH_RESET_TOKEN_FIELDNAME, token.trim()));

			return user;
		} catch (MongoException e) {
			String errorMessage = "MongoDB encountered an exception "
					+ "while attempting to find a user account by email address.";
			log.error(errorMessage, e);
			throw new SegueDatabaseException(errorMessage, e);
		}
	}

	@Override
	public void registerQuestionAttempt(final String userId,
			final String questionPageId, final String fullQuestionId,
			final QuestionValidationResponseDTO questionAttempt) throws SegueDatabaseException {
		// Since we are attaching our own auto mapper we have to do MongoJack configure on it. 
		ObjectMapper objectMapper = contentMapper.getContentObjectMapper();
		MongoJackModule.configure(objectMapper);
		
		JacksonDBCollection<QuestionAttemptUserRecord, String> jc = JacksonDBCollection.wrap(
				database.getCollection(QUESTION_ATTEMPTS_COLLECTION_NAME), QuestionAttemptUserRecord.class,
				String.class, objectMapper);

		try {
			BasicDBObject query = new BasicDBObject(
					Constants.USER_ID_FKEY_FIELDNAME, userId);
			
			DBCursor<QuestionAttemptUserRecord> questionAttemptRecord = jc.find(query);
			String questionRecordId = null;
			if (questionAttemptRecord.size() > 1) {
				// multiple records returned so this is ambiguous.
				throw new SegueDatabaseException(
						"Expected to only find one QuestionAttempt for the user, found "
								+ questionAttemptRecord.size());
			} else if (questionAttemptRecord.size() == 0) {
				// create a new QuestionAttemptUserRecord
				WriteResult<QuestionAttemptUserRecord, String> save = jc.save(new QuestionAttemptUserRecord(
						null, userId));
				
				questionRecordId = save.getSavedId();
				
				if (save.getError() != null) {
					log.error("Error during database update " + save.getError());
					throw new SegueDatabaseException(
							"Error occurred whilst trying to create new QuestionAttemptUserRecord: "
									+ save.getError());
				}
			} else {
				// set the question Record Id so that we can modify it for the update. 
				questionRecordId = questionAttemptRecord.toArray().get(0).getId();
			}

			WriteResult<QuestionAttemptUserRecord, String> r = jc.updateById(
					questionRecordId,
					DBUpdate.push(Constants.QUESTION_ATTEMPTS_FIELDNAME + "."
							+ questionPageId + "." + fullQuestionId,
							questionAttempt));
			
			if (r.getError() != null) {
				log.error("Error during database update " + r.getError());
				throw new SegueDatabaseException(
						"Error occurred whilst trying to update the users QuestionAttemptUserRecord: "
								+ r.getError());
			}
			
		} catch (MongoException e) {
			log.error("MongoDB Database Exception. ", e);
		}
	}
	
	@Override
	public QuestionAttemptUserRecord getQuestionAttempts(final String userId)
		throws SegueDatabaseException {
		
		// Since we are attaching our own auto mapper we have to do MongoJack configure on it. 
		ObjectMapper objectMapper = contentMapper.getContentObjectMapper();
		MongoJackModule.configure(objectMapper);
		
		JacksonDBCollection<QuestionAttemptUserRecord, String> jc = JacksonDBCollection.wrap(
				database.getCollection(QUESTION_ATTEMPTS_COLLECTION_NAME), QuestionAttemptUserRecord.class,
				String.class, objectMapper);

		try {
			BasicDBObject query = new BasicDBObject(
					Constants.USER_ID_FKEY_FIELDNAME, userId);
			
			DBCursor<QuestionAttemptUserRecord> questionAttemptRecord = jc.find(query);
			
			if (questionAttemptRecord.size() > 1) {
				throw new SegueDatabaseException(
						"Expected to only find one QuestionAttempt for the user, found "
								+ questionAttemptRecord.size());
			} else if (questionAttemptRecord.size() == 0) {
				return new QuestionAttemptUserRecord();
			}
			
			return questionAttemptRecord.toArray().get(0);
		} catch (MongoException e) {
			log.error("MongoDB Database Exception. ", e);
			throw new SegueDatabaseException("Database error.", e);
		}
	}

	@Override
	public final User getByLinkedAccount(final AuthenticationProvider provider,
			final String providerUserId) throws SegueDatabaseException {
		if (null == provider || null == providerUserId) {
			return null;
		}

		JacksonDBCollection<LinkedAccount, String> jc = JacksonDBCollection
				.wrap(database.getCollection(LINKED_ACCOUNT_COLLECTION_NAME),
						LinkedAccount.class, String.class);

		LinkedAccount linkAccount = jc.findOne(DBQuery.and(DBQuery.is(
				Constants.LINKED_ACCOUNT_PROVIDER_FIELDNAME, provider), DBQuery
				.is(Constants.LINKED_ACCOUNT_PROVIDER_USER_ID_FIELDNAME,
						providerUserId)));

		if (null == linkAccount) {
			return null;
		}

		return this.getById(linkAccount.getLocalUserId());
	}

	@Override
	public boolean hasALinkedAccount(final User user) throws SegueDatabaseException {
		JacksonDBCollection<LinkedAccount, String> jc = JacksonDBCollection
				.wrap(database.getCollection(LINKED_ACCOUNT_COLLECTION_NAME),
						LinkedAccount.class, String.class);

		BasicDBObject query = new BasicDBObject(
				Constants.LINKED_ACCOUNT_LOCAL_USER_ID_FIELDNAME, user
				.getDbId());
		try {
			DBCursor<LinkedAccount> linkAccounts = jc.find(query);
			
			if (linkAccounts.size() > 0) {
				return true;
			}
			
			return false;
		} catch (MongoException e) {
			String errorMessage = "MongoDB encountered an exception "
					+ "while attempting to find a user's linked accounts";
			log.error(errorMessage, e);
			throw new SegueDatabaseException(errorMessage, e);
		}
	}

	@Override
	public List<AuthenticationProvider> getAuthenticationProvidersByUser(final User user)
		throws SegueDatabaseException {
		Validate.notNull(user);
		Validate.notEmpty(user.getDbId());
		
		JacksonDBCollection<LinkedAccount, String> jc = JacksonDBCollection
				.wrap(database.getCollection(LINKED_ACCOUNT_COLLECTION_NAME),
						LinkedAccount.class, String.class);
		try {
			BasicDBObject query = new BasicDBObject(
					Constants.LINKED_ACCOUNT_LOCAL_USER_ID_FIELDNAME, user.getDbId());
			DBCursor<LinkedAccount> linkAccounts = jc.find(query);
			
			List<AuthenticationProvider> providersToReturn = Lists.newArrayList();
			for (LinkedAccount accountLinkRecord : linkAccounts) {
				providersToReturn.add(accountLinkRecord.getProvider());
			}
			
			return providersToReturn;
		} catch (MongoException e) {
			String errorMessage = "MongoDB encountered an exception "
					+ "while attempting to find a user's linked accounts";
			log.error(errorMessage, e);
			throw new SegueDatabaseException(errorMessage, e);
		}
	}
	
	@Override
	public void unlinkAuthProviderFromUser(final User user, final AuthenticationProvider provider)
		throws SegueDatabaseException {
		Validate.notNull(user);
		Validate.notNull(user.getDbId());
		Validate.notNull(provider);
		
		JacksonDBCollection<LinkedAccount, String> jc = JacksonDBCollection
				.wrap(database.getCollection(LINKED_ACCOUNT_COLLECTION_NAME),
						LinkedAccount.class, String.class);
		
		DBQuery.Query linkAccountToDeleteQuery = DBQuery.and(DBQuery.is(
				Constants.LINKED_ACCOUNT_PROVIDER_FIELDNAME, provider), DBQuery
				.is(Constants.LINKED_ACCOUNT_LOCAL_USER_ID_FIELDNAME,
						user.getDbId()));
		
		LinkedAccount linkAccountToDelete = jc.findOne(linkAccountToDeleteQuery);
		
		if (null == linkAccountToDelete) {
			throw new SegueDatabaseException("Unable to locate linkedAccount for deletion.");
		}
		
		jc.removeById(linkAccountToDelete.getId());
	}
	
	@Override
	public boolean linkAuthProviderToAccount(final User user,
			final AuthenticationProvider provider, final String providerUserId) throws SegueDatabaseException {
		JacksonDBCollection<LinkedAccount, String> jc = JacksonDBCollection
				.wrap(database.getCollection(LINKED_ACCOUNT_COLLECTION_NAME),
						LinkedAccount.class, String.class);
		try {
			WriteResult<LinkedAccount, String> r = jc.save(new LinkedAccount(null,
					user.getDbId(), provider, providerUserId));

			return null == r.getError();
		} catch (MongoException e) {
			String errorMessage = "MongoDB encountered an exception "
					+ "while attempting to link an auth provider to a user account.";
			log.error(errorMessage, e);
			throw new SegueDatabaseException(errorMessage, e);
		}

	}

	/**
	 * This method ensures that the collection is setup correctly and has all of
	 * the required indices.
	 */
	private void initialiseDataManager() {
		log.info("Initializing Mongo DB user collection indices.");
		database.getCollection(USER_COLLECTION_NAME).ensureIndex(
				new BasicDBObject(Constants.LOCAL_AUTH_EMAIL_FIELDNAME, 1),
				Constants.LOCAL_AUTH_EMAIL_FIELDNAME, true);
	}
}