/**
 * Copyright 2015 Stephen Cummins
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
package uk.ac.cam.cl.dtg.isaac.dos.eventbookings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

import org.elasticsearch.common.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.cl.dtg.segue.dao.ResourceNotFoundException;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.database.PostgresSqlDb;

import com.google.api.client.util.Lists;

/**
 * PgEventBookings.
 * 
 * Postgres aware EventBookings.
 *
 */
public class PgEventBookings implements EventBookings {
	private static final Logger log = LoggerFactory.getLogger(PgEventBookings.class);
	private PostgresSqlDb ds;

	/**
	 * 
	 * @param ds connection to the database.
	 */
	public PgEventBookings(final PostgresSqlDb ds) {
		this.ds = ds;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * uk.ac.cam.cl.dtg.isaac.dos.eventbookings.EventBookings#add(uk.ac.cam.
	 * cl.dtg.isaac.dos.eventbookings.EventBooking)
	 */
	@Override
	public EventBooking add(final String eventId, final String userId) throws SegueDatabaseException {
		PreparedStatement pst;
		try (Connection conn = ds.getDatabaseConnection()) {
			Date creationDate = new Date();
			pst = conn.prepareStatement(
					"INSERT INTO event_bookings (id, user_id, event_id, created) VALUES (DEFAULT, ?, ?, ?)",
					Statement.RETURN_GENERATED_KEYS);
			pst.setString(1, userId);
			pst.setString(2, eventId);
			pst.setTimestamp(3, new java.sql.Timestamp(creationDate.getTime()));

			if (pst.executeUpdate() == 0) {
				throw new SegueDatabaseException("Unable to save event booking.");
			}

			try (ResultSet generatedKeys = pst.getGeneratedKeys()) {
				if (generatedKeys.next()) {
					Long id = generatedKeys.getLong(1);
					return new PgEventBooking(ds, id, userId, eventId, creationDate);
				} else {
					throw new SQLException("Creating event booking failed, no ID obtained.");
				}
			}

		} catch (SQLException e) {
			throw new SegueDatabaseException("Postgres exception", e);
		}
	}

	@Override
	public void delete(final String eventId, final String userId) throws SegueDatabaseException {
		PreparedStatement pst;
		try (Connection conn = ds.getDatabaseConnection()) {
			pst = conn.prepareStatement("DELETE FROM event_bookings WHERE event_id = ? AND user_id = ?");
			pst.setString(1, eventId);
			pst.setString(2, userId);
			int executeUpdate = pst.executeUpdate();
			
			if (executeUpdate == 0) {
				throw new ResourceNotFoundException("Could not delete the requested booking.");
			}
			
		} catch (SQLException e) {
			throw new SegueDatabaseException("Postgres exception while trying to delete event booking", e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.cam.cl.dtg.isaac.dos.eventbookings.EventBookings#iterate()
	 */
	@Override
	public EventBooking findBookingByEventAndUser(final String eventId, final String userId)
		throws SegueDatabaseException {
		Validate.notBlank(eventId);

		try (Connection conn = ds.getDatabaseConnection()) {
			PreparedStatement pst;
			pst = conn.prepareStatement("Select * FROM event_bookings WHERE event_id = ? AND user_id = ?");
			pst.setString(1, eventId);
			pst.setString(2, userId);
			ResultSet results = pst.executeQuery();
			
			EventBooking result = null; 
			int count = 0;
			while (results.next()) {
				result = buildPgEventBooking(results);
				count++;
			}

			if (count == 1) {
				return result;
			} else if (count == 0) {
				throw new ResourceNotFoundException("Unable to locate the booking you requested.");
			} else {
				String msg = String.format(
						"Found more than one event booking that matches event id (%s) and user id (%s).",
						eventId, userId);
				log.error(msg);
				throw new SegueDatabaseException(msg);
			}
			
		} catch (SQLException e) {
			throw new SegueDatabaseException("Postgres exception", e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.cam.cl.dtg.isaac.dos.eventbookings.EventBookings#iterate()
	 */
	@Override
	public Iterable<EventBooking> findAll() throws SegueDatabaseException {
		try (Connection conn = ds.getDatabaseConnection()) {
			PreparedStatement pst;
			pst = conn.prepareStatement("Select * FROM event_bookings");

			ResultSet results = pst.executeQuery();
			List<EventBooking> returnResult = Lists.newArrayList();
			while (results.next()) {
				returnResult.add(buildPgEventBooking(results));

			}
			return returnResult;
		} catch (SQLException e) {
			throw new SegueDatabaseException("Postgres exception", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.cam.cl.dtg.isaac.dos.eventbookings.EventBookings#iterate()
	 */
	@Override
	public Iterable<EventBooking> findAllByEventId(final String eventId) throws SegueDatabaseException {
		Validate.notBlank(eventId);

		try (Connection conn = ds.getDatabaseConnection()) {
			PreparedStatement pst;
			pst = conn.prepareStatement("Select * FROM event_bookings WHERE event_id = ?");
			pst.setString(1, eventId);
			ResultSet results = pst.executeQuery();
			List<EventBooking> returnResult = Lists.newArrayList();
			while (results.next()) {
				returnResult.add(buildPgEventBooking(results));

			}
			return returnResult;
		} catch (SQLException e) {
			throw new SegueDatabaseException("Postgres exception", e);
		}
	}

	@Override
	public Iterable<EventBooking> findAllByUserId(final String userId) throws SegueDatabaseException {
		Validate.notBlank(userId);

		try (Connection conn = ds.getDatabaseConnection()) {
			PreparedStatement pst;
			pst = conn.prepareStatement("Select * FROM event_bookings WHERE user_id = ?");
			pst.setString(1, userId);
			ResultSet results = pst.executeQuery();
			
			List<EventBooking> returnResult = Lists.newArrayList();
			while (results.next()) {
				returnResult.add(buildPgEventBooking(results));

			}
			return returnResult;
		} catch (SQLException e) {
			throw new SegueDatabaseException("Postgres exception", e);
		}
	}
	
	/**
	 * Create a pgEventBooking from a results set.
	 * 
	 * Assumes there is a result to read.
	 * 
	 * @param results - the results to convert
	 * @return a new PgEventBooking 
	 * @throws SQLException - if an error occurs.
	 */
	private PgEventBooking buildPgEventBooking(final ResultSet results) throws SQLException {
		return new PgEventBooking(ds, results.getLong("id"), results.getString("user_id"),
				results.getString("event_id"),  results.getTimestamp("created"));
		
	}
}