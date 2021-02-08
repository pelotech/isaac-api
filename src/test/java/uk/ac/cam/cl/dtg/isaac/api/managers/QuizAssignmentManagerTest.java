/*
 * Copyright 2021 Raspberry Pi Foundation
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
package uk.ac.cam.cl.dtg.isaac.api.managers;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import uk.ac.cam.cl.dtg.isaac.IsaacTest;
import uk.ac.cam.cl.dtg.isaac.api.AbstractFacadeTest;
import uk.ac.cam.cl.dtg.isaac.api.services.EmailService;
import uk.ac.cam.cl.dtg.isaac.dao.IQuizAssignmentPersistenceManager;
import uk.ac.cam.cl.dtg.isaac.dos.QuizFeedbackMode;
import uk.ac.cam.cl.dtg.isaac.dto.QuizAssignmentDTO;
import uk.ac.cam.cl.dtg.segue.api.managers.UserAccountManager;
import uk.ac.cam.cl.dtg.segue.dao.SegueDatabaseException;
import uk.ac.cam.cl.dtg.segue.dao.content.ContentManagerException;
import uk.ac.cam.cl.dtg.util.PropertiesLoader;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.resetToNice;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.easymock.PowerMock.reset;
import static uk.ac.cam.cl.dtg.segue.api.Constants.HOST_NAME;

@RunWith(PowerMockRunner.class)
@PrepareForTest({UserAccountManager.class})
@PowerMockIgnore({ "javax.ws.*", "javax.management.*", "javax.script.*" })
public class QuizAssignmentManagerTest extends IsaacTest {

    private QuizAssignmentManager quizAssignmentManager;

    private IQuizAssignmentPersistenceManager quizAssignmentPersistenceManager;
    private EmailService emailService;

    private ImmutableList<QuizAssignmentDTO> allStudentAssignments;
    private QuizAssignmentDTO newAssignment;

    @Before
    public void setUp() throws ContentManagerException, SegueDatabaseException {
        PropertiesLoader properties = createMock(PropertiesLoader.class);
        emailService = createMock(EmailService.class);
        quizAssignmentPersistenceManager = createMock(IQuizAssignmentPersistenceManager.class);

        quizAssignmentManager = new QuizAssignmentManager(quizAssignmentPersistenceManager, emailService, quizManager, groupManager, properties);

        expect(properties.getProperty(HOST_NAME)).andStubReturn("example.com.invalid");

        replay(properties, emailService, quizAssignmentPersistenceManager);
    }

    @Before
    public void initializeAdditionalObjects() {
        newAssignment = new QuizAssignmentDTO(
            null, studentQuiz.getId(),
            teacher.getId(), studentGroup.getId(),
            somePastDate, someFutureDate,
            QuizFeedbackMode.OVERALL_MARK);

        allStudentAssignments = ImmutableList.<QuizAssignmentDTO>builder().addAll(studentAssignments).add(studentInactiveIgnoredAssignment).build();
    }

    @Test
    public void createAssignment() throws SegueDatabaseException, ContentManagerException {
        Long returnedId = 0xF00L;

        with(quizAssignmentPersistenceManager, m -> {
            expect(m.getAssignmentsByQuizIdAndGroup(
                studentQuiz.getId(), studentGroup.getId())).andReturn(Collections.emptyList());
            expect(m.saveAssignment(newAssignment)).andReturn(returnedId);
        });
        with(emailService, m -> m.sendAssignmentEmailToGroup(eq(newAssignment), eq(studentQuiz), anyObject(), eq("email-template-group-quiz-assignment")));

        QuizAssignmentDTO createdAssignment = quizAssignmentManager.createAssignment(newAssignment);

        assertEquals(returnedId, createdAssignment.getId());
        assertTrue(new Date().getTime() - createdAssignment.getCreationDate().getTime() < 1000);
    }

    @Test
    public void createAnotherAssignmentAfterFirstIsDueSucceeds() throws SegueDatabaseException, ContentManagerException {

        with(quizAssignmentPersistenceManager, m -> {
            expect(m.getAssignmentsByQuizIdAndGroup(
                studentQuiz.getId(), studentGroup.getId())).andReturn(Collections.singletonList(overdueAssignment));
            expect(m.saveAssignment(newAssignment)).andReturn(0L);
        });
        resetToNice(emailService);

        quizAssignmentManager.createAssignment(newAssignment);
    }

    @Test(expected = DueBeforeNowException.class)
    public void createAssignmentFailsInThePast() throws SegueDatabaseException, ContentManagerException {
        newAssignment.setDueDate(somePastDate);

        quizAssignmentManager.createAssignment(newAssignment);
    }

    @Test(expected = DuplicateAssignmentException.class)
    public void createDuplicateAssignmentFails() throws SegueDatabaseException, ContentManagerException {

        with(quizAssignmentPersistenceManager, m -> {
            expect(m.getAssignmentsByQuizIdAndGroup(
                studentQuiz.getId(), studentGroup.getId())).andReturn(Collections.singletonList(studentAssignment));
        });

        quizAssignmentManager.createAssignment(newAssignment);
    }

    @Test
    public void getAssignedQuizzes() throws SegueDatabaseException {
        with(quizAssignmentPersistenceManager, m -> {
            expect(m.getAssignmentsByGroupList(studentGroups)).andReturn(allStudentAssignments);
        });
        List<QuizAssignmentDTO> assignedQuizzes = quizAssignmentManager.getAssignedQuizzes(student);

        assertEquals(studentAssignments, assignedQuizzes);
    }

    @Test
    public void getActiveQuizAssignments() throws SegueDatabaseException {
        with(quizAssignmentPersistenceManager, m -> {
            expect(m.getAssignmentsByGroupList(studentGroups)).andReturn(allStudentAssignments);
        });

        List<QuizAssignmentDTO> activeQuizAssignments = quizAssignmentManager.getActiveQuizAssignments(studentQuiz, student);

        assertEquals(Collections.singletonList(studentAssignment), activeQuizAssignments);
    }

    private <T, E extends Exception> void with(T mock, AbstractFacadeTest.SickConsumer<T, E> setup) {
        reset(mock);
        try {
            setup.accept(mock);
        } catch (Exception e) {
            // This shouldn't happen.
            throw new RuntimeException("Error in mock setup", e);
        }
        replay(mock);
    }
}
