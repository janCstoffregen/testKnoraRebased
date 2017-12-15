/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.permissionmessages.{PermissionType, PermissionV1}
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.{CoreSpec, IRI, OntologyConstants, SharedAdminTestData}

import scala.collection.Map

object PermissionUtilV1Spec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}
class PermissionUtilV1Spec extends CoreSpec("PermissionUtilSpec") with ImplicitSender with Authenticator {

    val permissionLiteral = "RV knora-base:UnknownUser|V knora-base:KnownUser|M knora-base:ProjectMember|CR knora-base:Creator"

    val parsedPermissionLiteral = Map(
        "RV" -> Set(OntologyConstants.KnoraBase.UnknownUser),
        "V" -> Set(OntologyConstants.KnoraBase.KnownUser),
        "M" -> Set(OntologyConstants.KnoraBase.ProjectMember),
        "CR" -> Set(OntologyConstants.KnoraBase.Creator)
    )

    "PermissionUtil " should {

        "return user's max permission for a specific resource (incunabula normal project member user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://rdfh.ch/00014b43f902",
                subjectCreator = "http://rdfh.ch/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = permissionLiteral,
                userProfile = SharedAdminTestData.incunabulaMemberUser
            ) should equal(Some(6)) // modify permission
        }

        "return user's max permission for a specific resource (incunabula project admin user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://rdfh.ch/00014b43f902",
                subjectCreator = "http://rdfh.ch/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = permissionLiteral,
                userProfile = SharedAdminTestData.incunabulaProjectAdminUser
            ) should equal(Some(8)) // change rights permission
        }

        "return user's max permission for a specific resource (incunabula creator user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://rdfh.ch/00014b43f902",
                subjectCreator = "http://rdfh.ch/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = permissionLiteral,
                userProfile = SharedAdminTestData.incunabulaCreatorUser
            ) should equal(Some(8)) // change rights permission
        }

        "return user's max permission for a specific resource (root user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://rdfh.ch/00014b43f902",
                subjectCreator = "http://rdfh.ch/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = permissionLiteral,
                userProfile = SharedAdminTestData.rootUser
            ) should equal(Some(8)) // change rights permission
        }

        "return user's max permission for a specific resource (normal user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://rdfh.ch/00014b43f902",
                subjectCreator = "http://rdfh.ch/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = permissionLiteral,
                userProfile = SharedAdminTestData.normalUser
            ) should equal(Some(2)) // restricted view permission
        }

        "return user's max permission for a specific resource (anonymous user)" in {
            PermissionUtilV1.getUserPermissionV1(
                subjectIri = "http://rdfh.ch/00014b43f902",
                subjectCreator = "http://rdfh.ch/users/91e19f1e01",
                subjectProject = SharedAdminTestData.INCUNABULA_PROJECT_IRI,
                subjectPermissionLiteral = permissionLiteral,
                userProfile = SharedAdminTestData.anonymousUser
            ) should equal(Some(1)) // restricted view permission
        }

        "return user's max permission from assertions for a specific resource" in {
            val assertions: Seq[(IRI, String)] = Seq(
                (OntologyConstants.KnoraBase.AttachedToUser, "http://rdfh.ch/users/91e19f1e01"),
                (OntologyConstants.KnoraBase.AttachedToProject, SharedAdminTestData.INCUNABULA_PROJECT_IRI),
                (OntologyConstants.KnoraBase.HasPermissions, permissionLiteral)
            )
            PermissionUtilV1.getUserPermissionV1FromAssertions(
                subjectIri = "http://rdfh.ch/00014b43f902",
                assertions = assertions,
                userProfile = SharedAdminTestData.incunabulaMemberUser
            ) should equal(Some(6)) // modify permissions
        }


        "return user's max permission on link value" ignore {
            // TODO
        }

        "return parsed permissions string as 'Map[IRI, Set[String]]" in {
            PermissionUtilV1.parsePermissions(permissionLiteral) should equal(parsedPermissionLiteral)
        }


        "return parsed permissions string as 'Set[PermissionV1]' (object access permissions)" in {
            val hasPermissionsString = "M knora-base:Creator,knora-base:ProjectMember|V knora-base:KnownUser,http://rdfh.ch/groups/customgroup|RV knora-base:UnknownUser"

            val permissionsSet = Set(
                PermissionV1.modifyPermission(OntologyConstants.KnoraBase.Creator),
                PermissionV1.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                PermissionV1.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                PermissionV1.viewPermission("http://rdfh.ch/groups/customgroup"),
                PermissionV1.restrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
            )

            PermissionUtilV1.parsePermissionsWithType(Some(hasPermissionsString), PermissionType.OAP) should contain allElementsOf permissionsSet
        }

        "return parsed permissions string as 'Set[PermissionV1]' (administrative permissions)" in {
            val hasPermissionsString = "ProjectResourceCreateAllPermission|ProjectAdminAllPermission|ProjectResourceCreateRestrictedPermission <http://www.knora.org/ontology/00FF/images#bild>,<http://www.knora.org/ontology/00FF/images#bildformat>"

            val permissionsSet = Set(
                PermissionV1.ProjectResourceCreateAllPermission,
                PermissionV1.ProjectAdminAllPermission,
                PermissionV1.projectResourceCreateRestrictedPermission("http://www.knora.org/ontology/00FF/images#bild"),
                PermissionV1.projectResourceCreateRestrictedPermission("http://www.knora.org/ontology/00FF/images#bildformat")
            )

            PermissionUtilV1.parsePermissionsWithType(Some(hasPermissionsString), PermissionType.AP) should contain allElementsOf permissionsSet
        }

        "build a 'PermissionV1' object" in {
            PermissionUtilV1.buildPermissionObject(
                name = OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission,
                iris = Set("1", "2", "3")
            ) should equal(
                Set(
                    PermissionV1.projectResourceCreateRestrictedPermission("1"),
                    PermissionV1.projectResourceCreateRestrictedPermission("2"),
                    PermissionV1.projectResourceCreateRestrictedPermission("3")
                )
            )
        }

        "remove duplicate permissions" in {

            val duplicatedPermissions = Seq(
                PermissionV1.restrictedViewPermission("1"),
                PermissionV1.restrictedViewPermission("1"),
                PermissionV1.restrictedViewPermission("2"),
                PermissionV1.changeRightsPermission("2"),
                PermissionV1.changeRightsPermission("3"),
                PermissionV1.changeRightsPermission("3")
            )

            val deduplicatedPermissions = Set(
                PermissionV1.restrictedViewPermission("1"),
                PermissionV1.restrictedViewPermission("2"),
                PermissionV1.changeRightsPermission("2"),
                PermissionV1.changeRightsPermission("3")
            )

            val result = PermissionUtilV1.removeDuplicatePermissions(duplicatedPermissions)
            result.size should equal(deduplicatedPermissions.size)
            result should contain allElementsOf deduplicatedPermissions

        }

        "remove lesser permissions" in {
            val withLesserPermissions = Set(
                PermissionV1.restrictedViewPermission("1"),
                PermissionV1.viewPermission("1"),
                PermissionV1.modifyPermission("2"),
                PermissionV1.changeRightsPermission("1"),
                PermissionV1.deletePermission("2")
            )

            val withoutLesserPermissions = Set(
                PermissionV1.changeRightsPermission("1"),
                PermissionV1.deletePermission("2")
            )

            val result = PermissionUtilV1.removeLesserPermissions(withLesserPermissions, PermissionType.OAP)
            result.size should equal(withoutLesserPermissions.size)
            result should contain allElementsOf withoutLesserPermissions
        }

        "create permissions string" in {
            val permissions = Set(
                PermissionV1.changeRightsPermission("1"),
                PermissionV1.deletePermission("2"),
                PermissionV1.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                PermissionV1.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                PermissionV1.viewPermission(OntologyConstants.KnoraBase.KnownUser)
            )

            val permissionsString = "CR knora-base:Creator,1|D 2|M knora-base:ProjectMember|V knora-base:KnownUser"

            val result = PermissionUtilV1.formatPermissions(permissions, PermissionType.OAP)
            result should equal(permissionsString)

        }
    }
}
