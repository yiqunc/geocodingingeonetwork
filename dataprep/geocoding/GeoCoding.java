//=============================================================================
//===	Copyright (C) 2001-2007 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.geonet.services.dataprep.geocoding;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import jeeves.constants.Jeeves;
import jeeves.interfaces.Service;
import jeeves.server.ServiceConfig;
import jeeves.server.context.ServiceContext;
import jeeves.utils.Util;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.kernel.DataManager;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.postgresql.util.PSQLException;

//=============================================================================

/**
 * Inserts a new metadata to the system (data is validated)
 */

public class GeoCoding implements Service {
	// --------------------------------------------------------------------------
	// ---
	// --- Init
	// ---
	// --------------------------------------------------------------------------

	private String stylePath;
	/**
	 * Indicates whether the service is returning output in JSON format or other
	 * formats(e.g. XML) It should be set in service definition files
	 * (config*.xml like config-aurin.xml)
	 */
	private Boolean jsonOutput = false;
	private String cmd;

	public void init(String appPath, ServiceConfig params) throws Exception {
		this.stylePath = appPath + Geonet.Path.IMPORT_STYLESHEETS;
		this.jsonOutput = BooleanUtils.toBooleanObject(params
				.getValue("jsonOutput"));
		this.cmd = params.getValue("cmd");
	}

	// --------------------------------------------------------------------------
	// ---
	// --- Service
	// ---
	// --------------------------------------------------------------------------

	public Element exec(Element params, ServiceContext context)
			throws Exception {
		GeonetContext gc = (GeonetContext) context
				.getHandlerContext(Geonet.CONTEXT_NAME);
		// context.getUserSession().
		DataManager dataMan = gc.getDataManager();

		// Return response
		// Element results = new Element("results");
		JSONObject responseJSONObj = new JSONObject();
		if (cmd != null && "get".equals(cmd)) {
			String ezi_address = Util.getParam(params, "address");
			String suburb = Util.getParam(params, "suburb");
			if (StringUtils.isBlank(ezi_address))
				return null;
			if (StringUtils.isBlank(suburb))
				return null;
			ezi_address = ezi_address.trim();
			suburb = suburb.trim();
			HashMap<String, String> addrMap = extractAddressField(ezi_address,
					suburb);
			ResultSet eligibleRS = findRangeAddressInDB(addrMap, null);
			JSONArray resultsJSONArray = new JSONArray();
			while (eligibleRS.next()) {
				JSONObject resultJSONObj = new JSONObject();

				JSONObject geometryJSONObj = new JSONObject();
				JSONObject locationJSONObj = new JSONObject();
				locationJSONObj.put("lng", eligibleRS.getString("st_x_geom"));
				locationJSONObj.put("lat", eligibleRS.getString("st_y_geom"));

				geometryJSONObj.put("location", locationJSONObj);
				resultJSONObj.put("geometry", geometryJSONObj);
				//
				resultJSONObj.put("formatted_address",
						eligibleRS.getString("ezi_add"));
				//

				//
				JSONArray address_componentsJSONArray = new JSONArray();
				HashMap<String, Object> mapp = new HashMap<String, Object>();
				mapp.put("long_name", eligibleRS.getString("hse_num1"));
				mapp.put("short_name", eligibleRS.getString("hse_num1"));
				mapp.put("types", Arrays.asList("street_number"));
				address_componentsJSONArray.put(mapp);
				mapp = new HashMap<String, Object>();
				mapp.put("long_name",
						addrMap.get("roadName") + " " + addrMap.get("roadType"));
				mapp.put("short_name",
						addrMap.get("roadName") + " " + addrMap.get("roadType"));
				mapp.put("types", Arrays.asList("route"));
				address_componentsJSONArray.put(mapp);
				mapp = new HashMap<String, Object>();
				mapp.put("long_name", suburb);
				mapp.put("short_name", suburb);
				mapp.put("types", Arrays.asList("locality"));
				address_componentsJSONArray.put(mapp);
				mapp = new HashMap<String, Object>();
				mapp.put("long_name", eligibleRS.getString("postcode"));
				mapp.put("short_name", eligibleRS.getString("postcode"));
				mapp.put("types", Arrays.asList("postal_code"));
				address_componentsJSONArray.put(mapp);

				resultJSONObj.put("address_components",
						address_componentsJSONArray);
				resultsJSONArray.put(resultJSONObj);
			}
			responseJSONObj.put("results", resultsJSONArray);
			eligibleRS.close();

		} else if (cmd != null && "fetchtablecontent".equals(cmd)) {
			String dbInfo = Util.getParam(params, "dbInfo");
			String tableName = Util.getParam(params, "tableName");
			JSONObject dbInfoJSONObj = new JSONObject(dbInfo);
			Connection conn = jeeves.util.jdbc.DirectJDBCConnection
					.getNewConnection(dbInfoJSONObj.getString("ip"),
							dbInfoJSONObj.getString("port"),
							dbInfoJSONObj.getString("database_name"),
							dbInfoJSONObj.getString("username"),
							dbInfoJSONObj.getString("password"));
			// fetch columnnames
			JSONArray columnNamesJSONArray = getColumnNamesInJSON(tableName,
					dbInfoJSONObj, conn);
			//
			Statement stm = conn.createStatement();
			// stm.setFetchSize(10);
			ResultSet eligibleRS = stm.executeQuery(String.format(
					"select * from \"%s\"    limit 100 ", tableName));
			// ssss
			JSONArray resultsJSONArray = new JSONArray();
			while (eligibleRS.next()) {
				JSONObject resultJSONObj = new JSONObject();
				for (int i = 0; i < columnNamesJSONArray.length(); i++) {
					String columnName = columnNamesJSONArray.getJSONObject(i)
							.getString("name");
					resultJSONObj.put(columnName,
							eligibleRS.getString(columnName));
				}
				resultsJSONArray.put(resultJSONObj);
			}
			responseJSONObj.put("records", resultsJSONArray);
			responseJSONObj.put("fields", columnNamesJSONArray);
			eligibleRS.close();
			conn.close();

		} else if (cmd != null && "fetchtablenames".equals(cmd)) {
			String dbInfo = Util.getParam(params, "dbInfo");
			JSONObject dbInfoJSONObj = new JSONObject(dbInfo);
			Connection conn = jeeves.util.jdbc.DirectJDBCConnection
					.getNewConnection(dbInfoJSONObj.getString("ip"),
							dbInfoJSONObj.getString("port"),
							dbInfoJSONObj.getString("database_name"),
							dbInfoJSONObj.getString("username"),
							dbInfoJSONObj.getString("password"));
			// conn.createStatement().executeUpdate("ALTER TABLE users  ADD COLUMN column4 integer;");
			DatabaseMetaData databaseMetadata = conn.getMetaData();

			String[] types = new String[1];
			types[0] = "TABLE";
			// ResultSet rs = databaseMetadata.getColumns( null, null,
			// "address", null);
			ResultSet rs = conn.getMetaData().getTables(
					dbInfoJSONObj.getString("database_name"), "public", "%",
					types);
			JSONArray resultsJSONArray = new JSONArray();
			while (rs.next()) {
				JSONObject resultJSONObj = new JSONObject();
				System.out.println(rs.getObject("TABLE_NAME"));
				resultJSONObj.put("name", rs.getObject("TABLE_NAME"));
				resultsJSONArray.put(resultJSONObj);
			}
			responseJSONObj.put("results", resultsJSONArray);
			conn.close();

		} else if (cmd != null && "fetchcolumnnames".equals(cmd)) {
			String dbInfo = Util.getParam(params, "dbInfo");
			String tableName = Util.getParam(params, "tableName");
			JSONObject dbInfoJSONObj = new JSONObject(dbInfo);
			Connection conn = jeeves.util.jdbc.DirectJDBCConnection
					.getNewConnection(dbInfoJSONObj.getString("ip"),
							dbInfoJSONObj.getString("port"),
							dbInfoJSONObj.getString("database_name"),
							dbInfoJSONObj.getString("username"),
							dbInfoJSONObj.getString("password"));
			JSONArray resultsJSONArray = getColumnNamesInJSON(tableName,
					dbInfoJSONObj, conn);
			responseJSONObj.put("results", resultsJSONArray);
			conn.close();
		} else if (cmd != null && "geocode_database".equals(cmd)) {

			String dbInfo = Util.getParam(params, "dbInfo");
			String tableName = Util.getParam(params, "tableName");
			String ezi_address_column_name = Util.getParam(params,
					"ezi_address_column_name");
			String suburb_column_name = Util.getParam(params,
					"suburb_column_name");

			JSONObject dbInfoJSONObj = new JSONObject(dbInfo);
			Connection candidatedb_conn = jeeves.util.jdbc.DirectJDBCConnection
					.getNewConnection(dbInfoJSONObj.getString("ip"),
							dbInfoJSONObj.getString("port"),
							dbInfoJSONObj.getString("database_name"),
							dbInfoJSONObj.getString("username"),
							dbInfoJSONObj.getString("password"));
			try {
				candidatedb_conn
						.createStatement()
						.executeUpdate(
								String.format(
										"ALTER TABLE \"%s\"  ADD COLUMN geocoded_x character varying(20);",
										tableName));
			} catch (Exception e) {
				if (e instanceof PSQLException) {
					PSQLException plsqlExcep = (PSQLException) e;
					if (!"42701".equals(plsqlExcep.getSQLState())) {
						throw e;
					}
				} else
					throw e;

			}
			try {
				candidatedb_conn
						.createStatement()
						.executeUpdate(
								String.format(
										"ALTER TABLE \"%s\"  ADD COLUMN geocoded_y character varying(20);",
										tableName));
			} catch (Exception e) {
				if (e instanceof PSQLException) {
					PSQLException plsqlExcep = (PSQLException) e;
					if (!"42701".equals(plsqlExcep.getSQLState())) {
						throw e;
					}
				} else
					throw e;

			}
			try {
				candidatedb_conn
						.createStatement()
						.executeUpdate(
								String.format(
										"ALTER TABLE \"%s\"  ADD COLUMN geocoded_log text;",
										tableName));
			} catch (Exception e) {
				if (e instanceof PSQLException) {
					PSQLException plsqlExcep = (PSQLException) e;
					if (!"42701".equals(plsqlExcep.getSQLState())) {
						throw e;
					}
				} else
					throw e;

			}
			try {
				candidatedb_conn
						.createStatement()
						.executeUpdate(
								String.format(
										"ALTER TABLE \"%s\"  ADD COLUMN geocoded_geom geometry;",
										tableName));
			} catch (Exception e) {
				if (e instanceof PSQLException) {
					PSQLException plsqlExcep = (PSQLException) e;
					if (!"42701".equals(plsqlExcep.getSQLState())) {
						throw e;
					}
				} else
					throw e;

			}
			try {
				candidatedb_conn
						.createStatement()
						.executeUpdate(
								String.format(
										"ALTER TABLE \"%s\"  ADD COLUMN geocoded_result character varying(40);",
										tableName));
			} catch (Exception e) {
				if (e instanceof PSQLException) {
					PSQLException plsqlExcep = (PSQLException) e;
					if (!"42701".equals(plsqlExcep.getSQLState())) {
						throw e;
					}
				} else
					throw e;

			}
			DatabaseMetaData candidatedb_Metadata = candidatedb_conn
					.getMetaData();

			String candidate_primarykey_columnname = null;
			ResultSet candidatedb_primarykeys = candidatedb_Metadata
					.getPrimaryKeys(dbInfoJSONObj.getString("database_name"),
							"public", tableName);
			if (candidatedb_primarykeys.next()) {
				candidate_primarykey_columnname = candidatedb_primarykeys
						.getString("COLUMN_NAME");
			}
			if (candidate_primarykey_columnname == null)
				throw new Exception("No Primary key found for selected table ["
						+ tableName + "] in specified database");
			//
			String query = "select \"%s\" , \"%s\",\"%s\" from \"%s\" where ( (geocoded_x is  null) or (geocoded_y is  null) or (geocoded_geom is null) )";
			String fomatted_query = query.format(query,
					candidate_primarykey_columnname, ezi_address_column_name,
					suburb_column_name, tableName);

			ResultSet candidatedb_rs = candidatedb_conn.createStatement()
					.executeQuery(fomatted_query);
			//
			Statement candidatedb_statement = candidatedb_conn
					.createStatement();
			//
			Connection geocoding_info_conn = jeeves.util.jdbc.DirectJDBCConnection
					.getNewConnection("127.0.0.1", "5432", "geocoding",
							"postgres", "Qwert123");
			Statement geocoding_info_statement = geocoding_info_conn
					.createStatement();
			//
			JSONArray failureGeocodeRecords = new JSONArray();
			Integer success_geocode_count = 0;
			Integer failure_geocode_count = 0;
			int count = 0;
			while (candidatedb_rs.next()) {
				String candidate_primarykey_value = candidatedb_rs
						.getString(candidate_primarykey_columnname);
				String candidate_ezi_address_value = candidatedb_rs
						.getString(ezi_address_column_name);
				String candidate_suburb_value = candidatedb_rs
						.getString(suburb_column_name);
				if (StringUtils.isNotEmpty(candidate_suburb_value))
					candidate_suburb_value = candidate_suburb_value.trim();
				if (StringUtils.isBlank(candidate_ezi_address_value)) {
					JSONObject _jsonObject = new JSONObject();
					_jsonObject.put("id", candidate_primarykey_value);
					_jsonObject.put("failure reason",
							"EZI address column's value is empty");
					failureGeocodeRecords.put(_jsonObject);
					failure_geocode_count++;
					continue;
				}
				if (StringUtils.isBlank(candidate_suburb_value)) {
					JSONObject _jsonObject = new JSONObject();
					_jsonObject.put("id", candidate_primarykey_value);
					_jsonObject.put("failure reason",
							"suburb column's value is empty");
					failureGeocodeRecords.put(_jsonObject);
					failure_geocode_count++;
					continue;
				}
				HashMap<String, String> addrMap = null;
				JSONArray resultsJSONArray = new JSONArray();
				String candidate_st_x_geom = "";
				String candidate_st_y_geom = "";
				String candidate_geom = null;
				String geocoded_result = "FAILED";
				try {
					ResultSet eligibleRS = null;
					addrMap = extractAddressField(candidate_ezi_address_value,
							candidate_suburb_value);
					if (StringUtils.isEmpty(addrMap.get("plaqNumber"))
							|| StringUtils.isEmpty(addrMap.get("roadType"))
							|| StringUtils.isEmpty(addrMap.get("roadName"))) {
						System.out.println("candidate_ezi_address_value:"
								+ candidate_ezi_address_value);
						System.out.println("candidate_suburb_value:"
								+ candidate_suburb_value);
						JSONObject _jsonObject = new JSONObject();
						_jsonObject.put("id", candidate_primarykey_value);
						_jsonObject.put("addressMap", addrMap);
						_jsonObject.put("failure reason",
								"error in parsing address");
						failureGeocodeRecords.put(_jsonObject);
						resultsJSONArray = new JSONArray();
						resultsJSONArray.put(_jsonObject);
						failure_geocode_count++;

					} else {
						eligibleRS = findExactAddressInDB(addrMap,
								geocoding_info_statement);

					}
					boolean found_record = false;
					boolean exact_match = false;
					if (eligibleRS != null) {
						if (eligibleRS.next()) {
							found_record = true;
							exact_match = true;
						}
					}

					if (!found_record) {
						eligibleRS = findRangeAddressInDB(addrMap,
								geocoding_info_statement);
						if (eligibleRS != null) {
							if (eligibleRS.next()) {
								found_record = true;
								exact_match = false;
							}
						}

					}
					if (found_record) {
						candidate_st_x_geom = eligibleRS.getString("st_x_geom");
						candidate_st_y_geom = eligibleRS.getString("st_y_geom");
						candidate_geom = eligibleRS.getString("geom");
						//
						JSONObject resultJSONObj = new JSONObject();

						JSONObject geometryJSONObj = new JSONObject();
						JSONObject locationJSONObj = new JSONObject();
						locationJSONObj.put("lng",
								eligibleRS.getString("st_x_geom"));
						locationJSONObj.put("lat",
								eligibleRS.getString("st_y_geom"));

						geometryJSONObj.put("location", locationJSONObj);
						resultJSONObj.put("geometry", geometryJSONObj);
						//
						resultJSONObj.put("formatted_address",
								eligibleRS.getString("ezi_add"));
						//

						//
						JSONArray address_componentsJSONArray = new JSONArray();
						HashMap<String, Object> mapp = new HashMap<String, Object>();
						String hse_num2_rs = eligibleRS.getString("hse_num2");
						boolean long_short_term_added =false;
						if (StringUtils.isNotEmpty(hse_num2_rs)){
							Integer hse_num2 = Integer.parseInt(hse_num2_rs);
							if (!hse_num2.equals(0)){
								mapp.put("long_name", eligibleRS.getString("hse_num1")+"-"+hse_num2_rs);
								mapp.put("short_name", eligibleRS.getString("hse_num1")+"-"+hse_num2_rs);
								long_short_term_added=true;
							}
						}
						if (!long_short_term_added) {
							mapp.put("long_name",
									eligibleRS.getString("hse_num1"));
							mapp.put("short_name",
									eligibleRS.getString("hse_num1"));
						}
						mapp.put("types", Arrays.asList("street_number"));
						address_componentsJSONArray.put(mapp);
						mapp = new HashMap<String, Object>();
						mapp.put("long_name", addrMap.get("roadName") + " "
								+ addrMap.get("roadType"));
						mapp.put("short_name", addrMap.get("roadName") + " "
								+ addrMap.get("roadType"));
						mapp.put("types", Arrays.asList("route"));
						address_componentsJSONArray.put(mapp);
						mapp = new HashMap<String, Object>();
						mapp.put("long_name", addrMap.get("suburb"));
						mapp.put("short_name", addrMap.get("suburb"));
						mapp.put("types", Arrays.asList("locality"));
						address_componentsJSONArray.put(mapp);
						mapp = new HashMap<String, Object>();
						mapp.put("long_name", eligibleRS.getString("postcode"));
						mapp.put("short_name", eligibleRS.getString("postcode"));
						mapp.put("types", Arrays.asList("postal_code"));
						address_componentsJSONArray.put(mapp);

						resultJSONObj.put("address_components",
								address_componentsJSONArray);
						resultsJSONArray.put(resultJSONObj);
						if (exact_match)
							geocoded_result = "SUCCESSFUL_EXACT_MATCH";
						else
							geocoded_result = "SUCCESSFUL_PARTIAL_MATCH";
					} else {
						geocoded_result = "FAILED";
						JSONObject _jsonObject = new JSONObject();
						_jsonObject.put("id", candidate_primarykey_value);
						_jsonObject.put("addressMap", addrMap);
						_jsonObject.put("failure reason",
								"not found in geocoding info database");
						failureGeocodeRecords.put(_jsonObject);
						resultsJSONArray = new JSONArray();
						resultsJSONArray.put(_jsonObject);
						failure_geocode_count++;

					}

				} catch (Exception e) {
					System.out.println("candidate_ezi_address_value:"
							+ candidate_ezi_address_value);
					System.out.println("candidate_suburb_value:"
							+ candidate_suburb_value);
					e.printStackTrace();
					JSONObject _jsonObject = new JSONObject();
					_jsonObject.put("id", candidate_primarykey_value);
					_jsonObject.put("addressMap", addrMap);
					_jsonObject.put("failure reason",
							"exception in finding address in geocoding info database, exception is "
									+ e.getMessage());
					failureGeocodeRecords.put(_jsonObject);
					resultsJSONArray = new JSONArray();
					resultsJSONArray.put(_jsonObject);
					failure_geocode_count++;
					geocoded_result = "FAILED";
					continue;
				}
				if (StringUtils.isNotEmpty(candidate_geom)) {
					try {
						candidatedb_statement
								.executeUpdate(String
										.format("update  \"%s\" set \"%s\" ='%s' , \"%s\"='%s' ,\"%s\"='%s',\"%s\"='%s',\"%s\"='%s' where \"%s\" = '%s';",
												tableName,
												"geocoded_x",
												candidate_st_x_geom,
												"geocoded_y",
												candidate_st_y_geom,
												"geocoded_log",
												resultsJSONArray.toString(),
												"geocoded_geom",
												candidate_geom,
												"geocoded_result",
												geocoded_result,
												candidate_primarykey_columnname,
												candidate_primarykey_value));
						success_geocode_count++;
					} catch (Exception e) {
						System.out.println("######");
						e.printStackTrace();
					}

				} else {
					try {
						candidatedb_statement
								.executeUpdate(String
										.format("update  \"%s\" set  \"%s\"='%s',\"%s\"='%s'  where \"%s\" = '%s';",
												tableName,

												"geocoded_log",
												resultsJSONArray.toString(),

												"geocoded_result",
												geocoded_result,

												candidate_primarykey_columnname,
												candidate_primarykey_value));
					} catch (Exception e) {
						System.out.println("######");
						e.printStackTrace();
					}
				}
				if (count % 10 == 0)
					candidatedb_conn.commit();
				count++;
				System.out.println("successfully geocode record with id="
						+ candidate_primarykey_value);
				

			}
			candidatedb_statement.close();
			candidatedb_conn.close();

			geocoding_info_statement.close();
			geocoding_info_conn.close();
			responseJSONObj.put("success_count", success_geocode_count);
			responseJSONObj.put("failure_count", failure_geocode_count);
			responseJSONObj.put("failure_records", failureGeocodeRecords);

		}

		Element root = new Element(Jeeves.Elem.ROOT);
		// Element success = new Element("success");
		// success.setText("OK");
		// root.addContent(results);
		// root.addContent(success);

		if (BooleanUtils.isTrue(this.jsonOutput)) {
			XMLOutputter xx = new XMLOutputter();
			// String jsonContent = org.json.XML.toJSONObject(
			// xx.outputString(root)).toString();
			Element jsonElement = new Element("jsonElement");
			jsonElement.setText(responseJSONObj.toString(10));
			return jsonElement;
		} else
			return root;
	}

	private JSONArray getColumnNamesInJSON(String tableName,
			JSONObject dbInfoJSONObj, Connection conn) throws SQLException,
			JSONException {
		DatabaseMetaData candidatedb_Metadata = conn.getMetaData();

		ResultSet rs = candidatedb_Metadata.getColumns(
				dbInfoJSONObj.getString("database_name"), "public", tableName,
				null);
		JSONArray resultsJSONArray = new JSONArray();
		while (rs.next()) {
			JSONObject resultJSONObj = new JSONObject();
			System.out.println(rs.getObject("COLUMN_NAME"));
			resultJSONObj.put("name", rs.getObject("COLUMN_NAME"));
			resultJSONObj.put("type", rs.getObject("TYPE_NAME"));
			resultsJSONArray.put(resultJSONObj);
		}
		return resultsJSONArray;
	}

	private ResultSet findExactAddressInDB(HashMap<String, String> addrMap,
			Statement statement) throws Exception {
		if (statement == null) {
			Connection conn = jeeves.util.jdbc.DirectJDBCConnection
					.getNewConnection("127.0.0.1", "5432", "geocoding",
							"postgres", "Qwert123");
			statement = conn.createStatement();
		}

		String query = "select st_x(a.geom) as st_x_geom, st_y(a.geom) as st_y_geom , a.* from address a where a.road_type ='%s' and  a.road_name ='%s' "
				+ "and a.locality = '%s' "
				+ " and (	"
				+ "		 ( a.hse_num1 = %s  ) and (a.hse_num2 >= %s )"
				+ ") order by a.hse_num1";
		String fomatted_query = query.format(query, addrMap.get("roadType"),
				addrMap.get("roadName"), addrMap.get("suburb"),
				addrMap.get("plaqNumber"), addrMap.get("plaqNumber2"));

		ResultSet eligibleRS = statement.executeQuery(fomatted_query);
		return eligibleRS;
	}

	private ResultSet findRangeAddressInDB(HashMap<String, String> addrMap,
			Statement statement) throws Exception {
		if (statement == null) {
			Connection conn = jeeves.util.jdbc.DirectJDBCConnection
					.getNewConnection("127.0.0.1", "5432", "geocoding",
							"postgres", "Qwert123");
			statement = conn.createStatement();
		}

		String query = "select st_x(a.geom) as st_x_geom, st_y(a.geom) as st_y_geom , a.* from address a where a.road_type ='%s' and  a.road_name ='%s'"
				+ " and a.locality = '%s' "
				+ " and (	"
				+ "		 ( a.hse_num1 = %s or (a.hse_num1 >= %s and a.hse_num1 <= %s) or (a.hse_num1 < %s and a.hse_num2 > %s)  )"

				+ " or "

				+ " 		( (a.hse_num2 >= %s and a.hse_num2 <= %s) or (a.hse_num1 < %s and a.hse_num2 > %s)  )"

				+ ") order by a.hse_num1";
		String fomatted_query = query.format(query, addrMap.get("roadType"),
				addrMap.get("roadName"), addrMap.get("suburb"),

				addrMap.get("plaqNumber"), addrMap.get("plaqNumber"),
				addrMap.get("plaqNumber2"), addrMap.get("plaqNumber"),
				addrMap.get("plaqNumber"),

				addrMap.get("plaqNumber"), addrMap.get("plaqNumber2"),
				addrMap.get("plaqNumber2"), addrMap.get("plaqNumber2"));

		ResultSet eligibleRS = statement.executeQuery(fomatted_query);
		return eligibleRS;
	}

	private HashMap<String, String> extractAddressField(String ezi_address,
			String suburb) throws Exception {
		HashMap<String, String> addrMap = new HashMap<String, String>();
		addrMap.put("original_ezi_address", ezi_address);
		addrMap.put("suburb", suburb);
		String ezi_address_without_comma = ezi_address;
		if (ezi_address.indexOf(",")!=-1){
			StringTokenizer str_comma = new StringTokenizer(ezi_address, ",");
			ezi_address_without_comma =str_comma.nextToken();
		}
		
		StringTokenizer str = new StringTokenizer(ezi_address_without_comma, " ");
		Boolean plaqNumberFound = false;
		if (str.hasMoreTokens()) {
			String token = str.nextToken();
			if (StringUtils.isBlank(token))
				return null;
			if (token.indexOf("Unit") == 0 || token.indexOf("unit") == 0)
				token = str.nextToken();
			if (token.indexOf("Lot") == 0 || token.indexOf("lot") == 0)
				token = str.nextToken();

			if (token.indexOf("/") != -1) {
				token = token.substring(token.indexOf("/") + 1);
			}
			if (token.indexOf("\\") != -1) {
				token = token.substring(token.indexOf("\\") + 1);
			}
			while (token.length() > 0
					&& !StringUtils.isNumeric(token.substring(
							token.length() - 1, token.length()))) {
				token = token.substring(0, token.length() - 1);
			}
			if (token.indexOf("-") != -1) {
				addrMap.put("plaqNumber",
						token.substring(0, token.indexOf("-")));
				addrMap.put("plaqNumber2",
						token.substring(token.indexOf("-") + 1, token.length()));
				plaqNumberFound = true;

			}
			if (!plaqNumberFound) {

				if (!StringUtils.isNumeric(token)) {
					if (!StringUtils.isNumeric(token.substring(0,
							token.length() - 1))) {
						throw new Exception(
								"plaque number is mandatory, ezi_address="
										+ ezi_address);
					} else {
						String trimmed_token = token.substring(0,
								token.length() - 1);
						addrMap.put("plaqNumber", trimmed_token);
					}

				} else {
					addrMap.put("plaqNumber", token);
				}
			}

		}
		//
		StringBuffer strBuff = new StringBuffer(ezi_address_without_comma);
		String ezi_address_without_comma_reverse = strBuff.reverse().toString();
		StringTokenizer str_reverse = new StringTokenizer(ezi_address_without_comma_reverse,
				" ");
		String orig_roadType=null;
		if (str_reverse.hasMoreTokens()) {
			String token = str_reverse.nextToken();
			StringBuffer strBuff2 = new StringBuffer(token);
			//
			String roadType = strBuff2.reverse().toString();
			orig_roadType= roadType;
			if ("rd".equalsIgnoreCase(roadType))
				roadType = "ROAD";
			else if ("st".equalsIgnoreCase(roadType))
				roadType = "STREET";
			else if ("ave".equalsIgnoreCase(roadType))
				roadType = "AVENUE";
			else if ("cl".equalsIgnoreCase(roadType))
				roadType = "CLOSE";
			else if ("dr".equalsIgnoreCase(roadType))
				roadType = "DRIVE";
			else if ("bvd".equalsIgnoreCase(roadType))
				roadType = "BOULEVARD";
			else if ("hwy".equalsIgnoreCase(roadType))
				roadType = "HIGHWA";			 
			else if ("pl".equalsIgnoreCase(roadType))
				roadType = "PLACE";
			else if ("pkwy".equalsIgnoreCase(roadType))
				roadType = "PARKWAY";
			else if ("cres".equalsIgnoreCase(roadType))
				roadType = "CRESCENT";
			else if ("hwy".equalsIgnoreCase(roadType))
				roadType = "HIGHWAY";
			else if ("cct".equalsIgnoreCase(roadType))
				roadType = "CIRCUIT";
			else if ("pde".equalsIgnoreCase(roadType))
				roadType = "PARADE";
			else if ("ct".equalsIgnoreCase(roadType))
				roadType = "COURT";
			else if ("sq".equalsIgnoreCase(roadType))
				roadType = "SQUARE";
			//
			addrMap.put("roadType", roadType);
		}
		String roadName = "";
		while (str.hasMoreTokens()) {
			String token = str.nextToken();
			if (token.equals(orig_roadType))
				break;
			if (StringUtils.isBlank(roadName))
				roadName = token;
			else
				roadName += " " + token;

		}

		addrMap.put("roadName", roadName);

		if (addrMap != null) {
			Iterator<Entry<String, String>> iter = addrMap.entrySet()
					.iterator();
			while (iter.hasNext()) {
				Entry<String, String> entry = iter.next();
				String value = entry.getValue();
				value = value.toUpperCase();
				value = value.replaceAll("'", "\\\\'");
				addrMap.put(entry.getKey(), value);
			}
		}
		if (addrMap.containsKey("plaqNumber2")) {
			if (addrMap.get("plaqNumber2") == null)
				addrMap.put("plaqNumber2", "0");

		} else
			addrMap.put("plaqNumber2", "0");
		return addrMap;
	}

}
