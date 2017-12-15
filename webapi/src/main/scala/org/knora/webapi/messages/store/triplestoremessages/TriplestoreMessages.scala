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

package org.knora.webapi.messages.store.triplestoremessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.util.ErrorHandlingMap
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, TriplestoreResponseException}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsObject, JsString, JsValue, JsonFormat, NullOptions, RootJsonFormat, _}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

sealed trait TriplestoreRequest

/**
  * Simple message for initial actor functionality.
  */
case class HelloTriplestore(txt: String) extends TriplestoreRequest

/**
  * Simple message for checking the connection to the triplestore.
  */
case object CheckConnection extends TriplestoreRequest

/**
  * Represents a SPARQL SELECT query to be sent to the triplestore. A successful response will be a [[SparqlSelectResponse]].
  *
  * @param sparql the SPARQL string.
  */
case class SparqlSelectRequest(sparql: String) extends TriplestoreRequest

/**
  * Represents a response to a SPARQL SELECT query, containing a parsed representation of the response (JSON, etc.)
  * returned by the triplestore
  *
  * @param head    the header of the response, containing the variable names.
  * @param results the body of the response, containing rows of query results.
  */
case class SparqlSelectResponse(head: SparqlSelectResponseHeader, results: SparqlSelectResponseBody) {

    /**
      * Returns the contents of the first row of results.
      *
      * @return a [[Map]] representing the contents of the first row of results.
      */
    @throws[InconsistentTriplestoreDataException]("if the query returned no results.")
    def getFirstRow: VariableResultsRow = {
        if (results.bindings.isEmpty) {
            throw TriplestoreResponseException(s"A SPARQL query unexpectedly returned an empty result")
        }

        results.bindings.head
    }
}

/**
  * Represents the header of a JSON response to a SPARQL SELECT query.
  *
  * @param vars the names of the variables that were used in the SPARQL SELECT statement.
  */
case class SparqlSelectResponseHeader(vars: Seq[String])

/**
  * Represents the body of a JSON response to a SPARQL SELECT query.
  *
  * @param bindings the bindings of values to the variables used in the SPARQL SELECT statement.
  *                 Empty rows are not allowed.
  */
case class SparqlSelectResponseBody(bindings: Seq[VariableResultsRow]) {
    require(bindings.forall(_.rowMap.nonEmpty), "Empty rows are not allowed in a SparqlSelectResponseBody")
}


/**
  * Represents a row of results in a JSON response to a SPARQL SELECT query.
  *
  * @param rowMap a map of variable names to values in the row. An empty string is not allowed as a variable
  *               name or value.
  */
case class VariableResultsRow(rowMap: ErrorHandlingMap[String, String]) {
    require(rowMap.forall {
        case (key, value) => key.nonEmpty && value.nonEmpty
    }, "An empty string is not allowed as a variable name or value in a VariableResultsRow")
}

/**
  * Represents a SPARQL CONSTRUCT query to be sent to the triplestore. A successful response will be a
  * [[SparqlConstructResponse]].
  *
  * @param sparql the SPARQL string.
  */
case class SparqlConstructRequest(sparql: String) extends TriplestoreRequest

/**
  * A response to a [[SparqlConstructRequest]].
  *
  * @param statements a map of subject IRIs to statements about each subject.
  */
case class SparqlConstructResponse(statements: Map[IRI, Seq[(IRI, String)]])

/**
  * Represents a SPARQL CONSTRUCT query to be sent to the triplestore. A successful response will be a
  * [[SparqlExtendedConstructResponse]].
  *
  * @param sparql       the SPARQL string.
  */
case class SparqlExtendedConstructRequest(sparql: String) extends TriplestoreRequest

/**
  * A response to a [[SparqlExtendedConstructRequest]].
  *
  * @param statements a map of subject IRIs to statements about each subject.
  */
case class SparqlExtendedConstructResponse(statements: Map[IRI, Map[IRI, Seq[StringV2]]])

/**
  * Represents a SPARQL Update operation to be performed.
  *
  * @param sparql the SPARQL string.
  */
case class SparqlUpdateRequest(sparql: String) extends TriplestoreRequest

/**
  * Indicates that the requested SPARQL Update was executed and returned no errors..
  */
case class SparqlUpdateResponse()


/**
  * Represents a SPARQL ASK query to be sent to the triplestore. A successful response will be a
  * [[SparqlAskResponse]].
  *
  * @param sparql the SPARQL string.
  */
case class SparqlAskRequest(sparql: String) extends TriplestoreRequest

/**
  * Represents a response to a SPARQL ASK query, containing the result.
  *
  * @param result of the query.
  */
case class SparqlAskResponse(result: Boolean)

/**
  * Message for resetting the contents of the triplestore and loading a fresh set of data. The data needs to be
  * stored in an accessible path and supplied via the [[RdfDataObject]].
  *
  * @param rdfDataObjects contains a list of [[RdfDataObject]].
  */
case class ResetTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]) extends TriplestoreRequest

/**
  * Sent as a response to [[ResetTriplestoreContent]] if the request was processed successfully.
  */
case class ResetTriplestoreContentACK()

/**
  * Message for removing all content from the triple store.
  */
case class DropAllTriplestoreContent() extends TriplestoreRequest

/**
  * Sent as a response to [[DropAllTriplestoreContent]] if the request was processed successfully.
  */
case class DropAllTriplestoreContentACK()

/**
  * Inserts data into the triplestore.
  *
  * @param rdfDataObjects contains a list of [[RdfDataObject]].
  */
case class InsertTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]) extends TriplestoreRequest

/**
  * Sent as a response to [[InsertTriplestoreContent]] if the request was processed successfully.
  */
case class InsertTriplestoreContentACK()

/**
  * Initialize the triplestore. This will initiate the (re)creation of the repository and adding data to it.
  *
  * @param rdfDataObject contains a list of [[RdfDataObject]].
  */
case class InitTriplestore(rdfDataObject: RdfDataObject) extends TriplestoreRequest

/**
  * Initialization ((re)creation of repository and loading of data) is finished successfully.
  */
case class InitTriplestoreACK()

/**
  * Ask triplestore if it has finished initialization
  */
case class Initialized() extends TriplestoreRequest

/**
  * Response indicating whether the triplestore has finished initialization and is ready for processing messages
  *
  * @param initFinished indicates if actor initialization has finished
  */
case class InitializedResponse(initFinished: Boolean)


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Contains the path to the 'ttl' file and the name of the named graph it should be loaded in.
  *
  * @param path to the 'ttl' file
  * @param name of the named graph the data will be load into.
  */
case class RdfDataObject(path: String, name: String)


/**
  * Represents a string with an optional language tag.
  *
  * @param value    the string value.
  * @param language the optional language tag.
  */
case class StringV2(value: String, language: Option[String] = None)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about resources and their properties.
  */
trait TriplestoreJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit object StringV2Format extends JsonFormat[StringV2] {
        /**
          * Converts a [[StringV2]] to a [[JsValue]].
          *
          * @param string a [[StringV2]].
          * @return a [[JsValue]].
          */
        def write(string: StringV2): JsValue = {

            if (string.language.isDefined) {
                // have language tag
                JsObject(
                    Map(
                        "value" -> string.value.toJson,
                        "language" -> string.language.toJson
                    )
                )
            } else {
                // no language tag
                JsObject(
                    Map(
                        "value" -> string.value.toJson
                    )
                )
            }
        }

        /**
          * Converts a [[JsValue]] to a [[StringV2]].
          *
          * @param json a [[JsValue]].
          * @return a [[StringV2]].
          */
        def read(json: JsValue): StringV2 = json match {
            case stringWithLang: JsObject => stringWithLang.getFields("value", "language") match {
                case Seq(JsString(value), JsString(language)) => StringV2(
                    value = value,
                    language = Some(language)
                )
                case Seq(JsString(value)) => StringV2(
                    value = value,
                    language = None
                )
                case _ => throw DeserializationException("JSON object with 'value', or 'value' and 'language' fields expected.")
            }
            case JsString(value) => StringV2(value, None)
            case _ => throw DeserializationException("JSON object with 'value', or 'value' and 'language' expected. ")
        }
    }

    implicit val rdfDataObjectFormat: RootJsonFormat[RdfDataObject] = jsonFormat2(RdfDataObject)
    implicit val resetTriplestoreContentFormat: RootJsonFormat[ResetTriplestoreContent] = jsonFormat1(ResetTriplestoreContent)

}