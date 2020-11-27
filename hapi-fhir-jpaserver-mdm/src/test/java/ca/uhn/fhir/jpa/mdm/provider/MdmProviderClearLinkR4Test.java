package ca.uhn.fhir.jpa.mdm.provider;

import ca.uhn.fhir.mdm.api.MdmConstants;
import ca.uhn.fhir.mdm.api.MdmLinkSourceEnum;
import ca.uhn.fhir.jpa.entity.MdmLink;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;

import static ca.uhn.fhir.mdm.api.MdmMatchOutcome.POSSIBLE_MATCH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.fail;

public class MdmProviderClearLinkR4Test extends BaseLinkR4Test {
	protected Practitioner myPractitioner;
	protected StringType myPractitionerId;
	protected IAnyResource myPractitionerGoldenResource;
	protected StringType myPractitionerGoldenResourceId;

	@BeforeEach
	public void before() {
		super.before();
		myPractitioner = createPractitionerAndUpdateLinks(new Practitioner());
		myPractitionerId = new StringType(myPractitioner.getIdElement().getValue());
		myPractitionerGoldenResource = getGoldenResourceFromTargetResource(myPractitioner);
		myPractitionerGoldenResourceId = new StringType(myPractitionerGoldenResource.getIdElement().getValue());
	}

	@Test
	public void testClearAllLinks() {
		assertLinkCount(2);
		myMdmProviderR4.clearMdmLinks(null, myRequestDetails);
		assertNoLinksExist();
	}

	private void assertNoLinksExist() {
		assertNoPatientLinksExist();
		assertNoPractitionerLinksExist();
	}

	private void assertNoPatientLinksExist() {
		assertThat(getPatientLinks(), hasSize(0));
	}

	private void assertNoPractitionerLinksExist() {
		assertThat(getPractitionerLinks(), hasSize(0));
	}

	@Test
	public void testClearPatientLinks() {
		assertLinkCount(2);
		Patient read = myPatientDao.read(new IdDt(mySourcePatientId.getValueAsString()).toVersionless());
		assertThat(read, is(notNullValue()));
		myMdmProviderR4.clearMdmLinks(new StringType("Patient"), myRequestDetails);
		assertNoPatientLinksExist();
		try {
			myPatientDao.read(new IdDt(mySourcePatientId.getValueAsString()).toVersionless());
			fail();
		} catch (ResourceNotFoundException e) {}

	}
	@Test
	public void testPersonsWithMultipleHistoricalVersionsCanBeDeleted() {
		createPatientAndUpdateLinks(buildJanePatient());
		createPatientAndUpdateLinks(buildJanePatient());
		createPatientAndUpdateLinks(buildJanePatient());
		createPatientAndUpdateLinks(buildJanePatient());
		Patient patientAndUpdateLinks = createPatientAndUpdateLinks(buildJanePatient());
		IAnyResource person = getGoldenResourceFromTargetResource(patientAndUpdateLinks);
		assertThat(person, is(notNullValue()));
		myMdmProviderR4.clearMdmLinks(null, myRequestDetails);
		assertNoPatientLinksExist();
		person = getGoldenResourceFromTargetResource(patientAndUpdateLinks);
		assertThat(person, is(nullValue()));
	}

	@Test
	public void testPersonWithLinksToOtherPersonsCanBeDeleted() {
		createPatientAndUpdateLinks(buildJanePatient());
		Patient patientAndUpdateLinks1 = createPatientAndUpdateLinks(buildJanePatient());
		Patient patientAndUpdateLinks = createPatientAndUpdateLinks(buildPaulPatient());

		IAnyResource personFromTarget = getGoldenResourceFromTargetResource(patientAndUpdateLinks);
		IAnyResource personFromTarget2 = getGoldenResourceFromTargetResource(patientAndUpdateLinks1);
		linkPersons(personFromTarget, personFromTarget2);

		//SUT
		myMdmProviderR4.clearMdmLinks(null, myRequestDetails);

		assertNoPatientLinksExist();
		IBundleProvider search = myPatientDao.search(buildGoldenResourceParameterMap());
		assertThat(search.size(), is(equalTo(0)));
	}

	/**
	 * Build a SearchParameterMap which looks up Golden Records (Source resources).
	 * @return
	 */
	private SearchParameterMap buildGoldenResourceParameterMap() {
		return new SearchParameterMap().setLoadSynchronous(true).add("_tag", new TokenParam(MdmConstants.SYSTEM_MDM_MANAGED, MdmConstants.CODE_HAPI_MDM_MANAGED));
	}

	@Test
	public void testPersonsWithCircularReferenceCanBeCleared() {
		Patient patientAndUpdateLinks = createPatientAndUpdateLinks(buildPaulPatient());
		Patient patientAndUpdateLinks1 = createPatientAndUpdateLinks(buildJanePatient());
		Patient patientAndUpdateLinks2 = createPatientAndUpdateLinks(buildFrankPatient());

		IAnyResource personFromTarget = getGoldenResourceFromTargetResource(patientAndUpdateLinks);
		IAnyResource personFromTarget1 = getGoldenResourceFromTargetResource(patientAndUpdateLinks1);
		IAnyResource personFromTarget2 = getGoldenResourceFromTargetResource(patientAndUpdateLinks2);

		// A -> B -> C -> A linkages.
		linkPersons(personFromTarget, personFromTarget1);
		linkPersons(personFromTarget1, personFromTarget2);
		linkPersons(personFromTarget2, personFromTarget);

		//SUT
		Parameters parameters = myMdmProviderR4.clearMdmLinks(null, myRequestDetails);

		printLinks();

		assertNoPatientLinksExist();
		IBundleProvider search = myPatientDao.search(buildGoldenResourceParameterMap());
		assertThat(search.size(), is(equalTo(0)));

	}

	//TODO GGG unclear if we actually need to reimplement this.
	private void linkPersons(IAnyResource theGoldenResource, IAnyResource theTargetResource) {
		// TODO NG - Should be ok to leave this - not really
		// throw new UnsupportedOperationException("We need to fix this!");
		myMdmLinkDaoSvc.createOrUpdateLinkEntity(theGoldenResource, theTargetResource, POSSIBLE_MATCH, MdmLinkSourceEnum.AUTO, createContextForCreate("Patient"));
	}

	@Test
	public void testClearPractitionerLinks() {
		assertLinkCount(2);
		Practitioner read = myPractitionerDao.read(new IdDt(myPractitionerGoldenResourceId.getValueAsString()).toVersionless());
		assertThat(read, is(notNullValue()));
		myMdmProviderR4.clearMdmLinks(new StringType("Practitioner"), myRequestDetails);
		assertNoPractitionerLinksExist();
		try {
			myPractitionerDao.read(new IdDt(myPractitionerGoldenResourceId.getValueAsString()).toVersionless());
			fail();
		} catch (ResourceNotFoundException e) {}
	}

	@Test
	public void testClearInvalidTargetType() {
		try {
			myMdmProviderR4.clearMdmLinks(new StringType("Observation"), myRequestDetails);
			fail();
		} catch (InvalidRequestException e) {
			assertThat(e.getMessage(), is(equalTo("$mdm-clear does not support resource type: Observation")));
		}
	}

	@Nonnull
	protected List<MdmLink> getPractitionerLinks() {
		return myMdmLinkDaoSvc.findMdmLinksByTarget(myPractitioner);
	}
}
