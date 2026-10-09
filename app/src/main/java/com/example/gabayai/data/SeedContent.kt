package com.example.gabayai.data

import com.example.gabayai.domain.Explanation
import com.example.gabayai.domain.FieldDefinition
import com.example.gabayai.domain.FieldSource
import com.example.gabayai.domain.FormDefinition
import com.example.gabayai.domain.Grounding
import com.example.gabayai.domain.GuideLanguage
import com.example.gabayai.domain.TranslationReview

/**
 * Public content migrated from the preserved AppBuilders reference after the
 * one-field physical-device gate passed. No regional translation was invented.
 * Examples are fictional teaching content, never extracted or supplied answers.
 * Source meaning review and independent language review are separate statuses.
 * backend/templates.py SHA256 f9b17df9b2e989a5b8f992913c892a914d682dbaed2dd0a94cb5ead23be3f1d2
 * backend/general_forms.py SHA256 cee7336e27436a2d31213397b1957c7d1686ff798a5a840c11515a44afeadc8d
 * frontend/src/field-guidance.ts SHA256 e8c58343042a868eec16025a6a259fcf4002b3a5828d09303a294aef9e3eea8a
 */
object SeedContent {
    const val PSA_FORM_ID = "psa-birth-application"
    private val unreviewed = TranslationReview.NOT_LINGUISTICALLY_REVIEWED
    val forms = listOf(FormDefinition(
        id = PSA_FORM_ID,
        name = "PSA Birth Certificate Application",
        anchors = listOf("birth certificate", "application form", "philippine statistics authority", "birth certificate details", "purpose of your request", "requester s details"),
        mandatoryAnchors = listOf("birth certificate", "application form"),
        minimumAnchorCount = 4,
    ))
    val fields = listOf(
        FieldDefinition(
            id = "psa.last_name", kind = "last_name", label = "Last Name (if female, last name before marriage)",
            aliases = listOf("last name if female"), formId = PSA_FORM_ID,
            source = FieldSource(
                grounding = Grounding.SOURCE_BACKED,
                excerpt = "Last Name (if female, last name before marriage)",
                url = "https://crs-appointment.psahelpline.ph/forms/birth-new.pdf",
                assetPath = "source/psa-birth.pdf",
                sha256 = "c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060",
                sourceReviewedOn = "2026-10-09",
            ),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Apelyido", "Ilagay ang apelyido ng taong kinukuhaan ng birth certificate. Kung babae, gamitin ang apelyido bago ikasal.", "Dela Cruz", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Last name", "Write the last name of the person on the birth certificate. For a woman, use her last name before marriage.", "Dela Cruz", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "psa.first_name", kind = "first_name", label = "First Name",
            aliases = listOf("first name include", "first name"), formId = PSA_FORM_ID,
            source = FieldSource(
                grounding = Grounding.SOURCE_BACKED,
                excerpt = "First Name (include JR., SR., II, III, IV, etc., if applicable)",
                url = "https://crs-appointment.psahelpline.ph/forms/birth-new.pdf",
                assetPath = "source/psa-birth.pdf",
                sha256 = "c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060",
                sourceReviewedOn = "2026-10-09",
            ),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Unang pangalan", "Ilagay ang first name o unang pangalan. Isama ang Jr., Sr., II, III, o IV kung meron sa pangalan.", "Juan", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "First name", "Write the person’s first name. Include JR., SR., II, III, or IV when applicable.", "Juan", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "psa.birth_date", kind = "birth_date", label = "Date of Birth",
            aliases = listOf("date of birth"), formId = PSA_FORM_ID,
            source = FieldSource(
                grounding = Grounding.SOURCE_BACKED,
                excerpt = "Date of Birth / Month / Day / Year",
                url = "https://crs-appointment.psahelpline.ph/forms/birth-new.pdf",
                assetPath = "source/psa-birth.pdf",
                sha256 = "c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060",
                sourceReviewedOn = "2026-10-09",
            ),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Kailan ipinanganak?", "Ilagay ang buwan, araw, at taon ng birthday.", "Marso 15, 1965", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Date of birth", "Enter the person’s birth month, day, and year.", "March 15, 1965", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "psa.birth_place", kind = "birth_place", label = "Place of Birth",
            aliases = listOf("place of birth"), formId = PSA_FORM_ID,
            source = FieldSource(
                grounding = Grounding.SOURCE_BACKED,
                excerpt = "City/Municipality and Province (Country if born abroad)",
                url = "https://crs-appointment.psahelpline.ph/forms/birth-new.pdf",
                assetPath = "source/psa-birth.pdf",
                sha256 = "c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060",
                sourceReviewedOn = "2026-10-09",
            ),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Saan ipinanganak?", "Ilagay ang lungsod o bayan, at probinsya kung saan ipinanganak. Kung sa abroad ipinanganak, ilagay ang bansa.", "Calamba, Laguna", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Where were they born?", "Write the city or municipality and province of birth. If born abroad, write the country.", "Calamba, Laguna", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "psa.bren", kind = "bren", label = "BReN, if known",
            aliases = listOf("bren if known", "birth reference number"), formId = PSA_FORM_ID,
            source = FieldSource(
                grounding = Grounding.SOURCE_BACKED,
                excerpt = "BReN, if known (Birth Reference Number). The BReN can be found on the previously issued PSA copy of the birth certificate of the person/child, if any.",
                url = "https://crs-appointment.psahelpline.ph/forms/birth-new.pdf",
                assetPath = "source/psa-birth.pdf",
                sha256 = "c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060",
                sourceReviewedOn = "2026-10-09",
            ),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Numero sa dating PSA birth certificate (BReN)", "Kung alam mo ang BReN (Birth Reference Number), kopyahin ito mula sa lumang PSA birth certificate. Pwede ring laktawan kung hindi alam dahil nakasulat na “if known”.", null, unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Number on an earlier PSA birth certificate (BReN)", "If you know the Birth Reference Number, copy it from a previously issued PSA birth certificate. This field says “if known”.", null, unreviewed),
            ),
        ),
        FieldDefinition(
            id = "psa.mobile", kind = "mobile", label = "Mobile Number",
            aliases = listOf("mobile number"), formId = PSA_FORM_ID,
            source = FieldSource(
                grounding = Grounding.SOURCE_BACKED,
                excerpt = "REQUESTER’S DETAILS / Mobile Number",
                url = "https://crs-appointment.psahelpline.ph/forms/birth-new.pdf",
                assetPath = "source/psa-birth.pdf",
                sha256 = "c777a6389c78cd6fa1fd96281b8094e1a0dba7abe41d0672749892dd70a24060",
                sourceReviewedOn = "2026-10-09",
            ),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Cellphone ng humihiling", "Ilagay ang cellphone number ng taong nagre-request ng birth certificate.", "0912 345 6789", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Requester's cellphone number", "Write the mobile number of the person requesting the certificate.", "0912 345 6789", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.birth_date", kind = "birth_date", label = "Date of birth",
            aliases = listOf("date of birth", "birth date", "birthdate", "birthday", "dob", "petsa ng kapanganakan", "araw ng kapanganakan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Kailan ipinanganak?", "Ito ang birthday ng taong tinutukoy sa form. Sundin ang format ng date na nakasulat sa form.", "Marso 15, 1965", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Date of birth", "This is the birth date of the person named in this part of the form. Follow the date format printed on the form.", "March 15, 1965", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.birth_place", kind = "birth_place", label = "Place of birth",
            aliases = listOf("place of birth", "birth place", "lugar ng kapanganakan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Saan ipinanganak?", "Ito ang lugar kung saan ipinanganak ang taong tinutukoy. Tingnan kung bayan, lungsod, probinsya, o bansa ang hinihingi sa form.", "Calamba, Laguna", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Place of birth", "This is where the person named here was born. Check whether the form asks for a town, city, province, or country.", "Calamba, Laguna", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.first_name", kind = "first_name", label = "First name",
            aliases = listOf("first name", "first names", "firstname", "given name", "given names", "unang pangalan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Unang pangalan", "First name o unang pangalan ito ng taong tinutukoy sa bahaging ito ng form.", "Juan", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "First name", "This is the first name of the person identified in this part of the form.", "Juan", unreviewed),
            ),
        ),
        // The reference's Filipino parent-maiden-name generalization was removed:
        // middle names vary. This uses the neutral meaning in its English entry.
        FieldDefinition(
            id = "general.middle_name", kind = "middle_name", label = "Middle name",
            aliases = listOf("middle name", "gitnang pangalan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Gitnang pangalan (Middle Name)", "Ito ang middle name ng taong tinutukoy. Middle initial ang unang letra lang nito; tingnan kung alin ang hinihingi sa form.", "Reyes", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Middle name", "This is the person's middle name. A middle initial is only its first letter; check which the form asks for.", "Reyes", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.middle_initial", kind = "middle_initial", label = "Middle initial",
            aliases = listOf("middle initial"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Middle Initial", "Unang letra lang ito ng middle name ng taong tinutukoy (halimbawa, R kung Reyes).", "R.", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Middle initial", "This is the first letter of the person's middle name.", "R.", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.last_name", kind = "last_name", label = "Last name",
            aliases = listOf("last name", "lastname", "surname", "family name", "apelyido"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Apelyido", "Apelyido o last name ito ng taong tinutukoy. Kung may nakalagay na tagubilin tulad ng apelyido bago ikasal, sundin iyon.", "Dela Cruz", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Last name", "This is the person's family name. Check any extra wording, such as a name before marriage, on the form.", "Dela Cruz", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.email", kind = "email", label = "Email address",
            aliases = listOf("email", "e mail", "email address", "emailaddress", "e mail address"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Email address", "Email address ito ng taong tinutukoy (halimbawa: juan@example.com), hindi address ng bahay.", "juan@example.com", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Email address", "This is the email address of the person identified here, rather than a home address.", "juan@example.com", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.mobile", kind = "mobile", label = "Phone number",
            aliases = listOf("mobile number", "mobile no", "cellphone", "cell phone", "phone number", "phone", "contact number", "contact no", "telephone", "contact phone", "telepono", "numero ng cellphone"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Cellphone number", "Cellphone number ito o contact number ng taong tinutukoy sa form.", "0912 345 6789", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Phone number", "This is the phone number of the person named in this label or section.", "0912 345 6789", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.address", kind = "address", label = "Address",
            aliases = listOf("address", "tirahan", "residential address", "mailing address"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Address o tirahan", "Address ito o lugar kung saan nakatira. I-check kung kasalukuyang tirahan (present address) o permanenteng address ang hinihingi.", "123 Mabini Street, Barangay San Jose, Calamba, Laguna", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Address", "Use the type of address named in the label, such as a current, permanent, or mailing address.", "123 Mabini Street, Barangay San Jose, Calamba, Laguna", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.employer", kind = "employer", label = "Employer or company",
            aliases = listOf("employer", "company name", "name of company", "pinagtatrabahuhan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Kumpanya o employer", "Pangalan ito ng kumpanya o pinagtatrabahuhan. Kung hindi ka nagtatrabaho o hindi applicable sa 'yo, sundin ang instruction ng form kung ano ang ilalagay (tulad ng N/A).", "Halimbawang Kompanya", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Employer or company", "This asks for the employer or company named by the field. Check the form's instructions if this does not apply to you.", "Example Company", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.job_title", kind = "job_title", label = "Job you are applying for",
            aliases = listOf("position applied for", "job applied for", "desired position", "posisyong inaaplayan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Trabahong inaaplayan", "Pangalan ito ng trabaho o position na inaaplayan mo. Iba ito sa pangalan ng kumpanya.", "Office Assistant", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Job you are applying for", "This is the job you are applying for, rather than the company's name.", "Office Assistant", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.occupation", kind = "occupation", label = "Occupation",
            aliases = listOf("occupation", "profession", "hanapbuhay", "trabaho", "job title"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Trabaho o hanapbuhay", "Ito ang trabaho o hanapbuhay ng taong tinutukoy sa form.", "Guro", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Occupation", "This is the work or occupation of the person identified in this part of the form.", "Teacher", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.school", kind = "school", label = "School",
            aliases = listOf("school", "university", "college name", "paaralan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Paaralan o eskwelahan", "Pangalan ito ng eskwelahan o paaralan. I-check sa form kung kasalukuyan o dating eskwelahan ang hinihingi.", "Halimbawang Paaralan", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "School", "This is the school named by the field. Check whether it asks for a current or former school.", "Example School", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.course", kind = "course", label = "Course or program",
            aliases = listOf("course", "degree program", "program of study", "kurso"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Kurso", "Pangalan ito ng kurso o program na kinuha o kinukuha sa eskwelahan.", "Bachelor of Science in Information Technology", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Course or program", "This is the name of the course or study program identified by the form.", "Bachelor of Science in Information Technology", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.age", kind = "age", label = "Age",
            aliases = listOf("age", "edad"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Edad", "Edad ito ng taong tinutukoy sa form. I-check kung may partikular na petsa kung kailan bibilangin ang edad.", "60", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Age", "This is the person's age. Check whether the form gives a date to use when stating it.", "60", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.civil_status", kind = "civil_status", label = "Marital status",
            aliases = listOf("civil status", "marital status", "katayuang sibil"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Civil status", "Civil status ito (kung Single, Married, o Biyudo/Biyuda). Piliin lang ang tamang status mo mula sa mga pagpipilian sa form.", "Single (walang asawa)", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Marital status", "This describes marital status. Choose an applicable answer from the choices printed on the form.", "Single", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.nationality", kind = "nationality", label = "Nationality or citizenship",
            aliases = listOf("nationality", "citizenship", "pagkamamamayan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Citizenship o nationality", "Citizenship o nationality ito (tulad ng Filipino). Kung may dual citizenship o hindi sigurado, magtanong sa nagbibigay ng form.", "Filipino", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Nationality or citizenship", "This asks for the nationality or citizenship named in the label. Ask the issuer if your situation is unclear.", "Filipino", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.relationship", kind = "relationship", label = "Relationship",
            aliases = listOf("relationship", "kaugnayan", "relasyon"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Relasyon sa tao (Relationship)", "Relasyon mo ito sa taong tinutukoy sa form (halimbawa: Magulang, Anak, o Asawa). I-check kung kaninong relasyon ang tinatanong.", "Anak", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Relationship", "This is the relationship to the person named in the label, such as a parent or child. Check whose relationship the form asks for.", "Child", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.purpose", kind = "purpose", label = "Purpose or reason",
            aliases = listOf("purpose", "reason for request", "dahilan", "layunin", "pakay"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Dahilan o pakay", "Dahilan ito kung bakit ka nagre-request o nag-a-apply (halimbawa: Para sa passport o trabaho). Kung may checklist sa form, pumili roon.", "Para sa personal na record", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Purpose or reason", "This is the reason for the request or application. If the form provides choices, use those printed choices.", "For personal records", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.id_number", kind = "id_number", label = "Identification number",
            aliases = listOf("id number", "identification number", "tin", "sss number", "sss no", "philhealth number", "pag ibig number"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Numero ng ID o account", "Numero ito ng ID o account na hinihingi sa label (tulad ng SSS, PhilHealth, o TIN). Kopyahin ito mula sa ID mo; huwag manghula ng number.", null, unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Identification number", "This is the specific ID or account number named in the label. Copy it from the relevant document; do not invent a number.", null, unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.reference_number", kind = "reference_number", label = "Reference number",
            aliases = listOf("reference number", "reference no", "reference code", "tracking number", "application number", "control number"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Reference number o control number", "Reference number ito, tracking number, o control code ng request mo. Kung wala ka pa nito o para sa opisina pala 'to, magtanong sa nagbigay ng form.", null, unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Reference number", "This is a reference number or code for the request. The label alone does not say who provides it; ask the issuer if you do not have it.", null, unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.signature", kind = "signature", label = "Signature",
            aliases = listOf("signature", "lagda", "pirma"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Pirma (Signature)", "Pirma ito ng taong tinutukoy sa form. Siguraduhing basahin muna ang nakasulat bago pumirma.", null, unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Signature", "A signature is the named person's own signed mark. Read the form's wording before signing.", null, unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.date", kind = "date", label = "Date",
            aliases = listOf("date", "petsa"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Petsa", "Petsa ito. Tingnan ang katabing nakasulat para malaman kung petsa ba ito ng pagpirma, pag-apply, o ibang date.", "Marso 15, 2026", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Date", "This asks for a date. Check the nearby wording to see whether it means the application, signing, or another event.", "March 15, 2026", unreviewed),
            ),
        ),
        FieldDefinition(
            id = "general.full_name", kind = "full_name", label = "Name",
            aliases = listOf("full name", "complete name", "name", "pangalan"),
            source = FieldSource(Grounding.GENERAL_VOCABULARY, null, null, null, null, null),
            explanations = listOf(
                Explanation(GuideLanguage.FILIPINO, "Buong pangalan", "Buong pangalan ito ng taong tinutukoy sa form. I-check kung pangalan mo ba o pangalan ng ibang tao (tulad ng magulang o asawa) ang hinihingi.", "Juan Dela Cruz", unreviewed),
                Explanation(GuideLanguage.ENGLISH, "Name", "This is the name of the person identified in this label or section. It may ask for your name or another person's name.", "Juan Dela Cruz", unreviewed),
            ),
        ),
    )
}
