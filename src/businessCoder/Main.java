/**
 * BusinessCoder New Data Analysis Project
 * Main.java
 * 
 * Inputs: SQL Table 
 * 
 * Outputs: "htmlOutput.html" -- HTML file which is a formatted report with the following statistics: 
 * 		Total Entries from SQL Table,
 * 		Total Distinct Entries (distinct company name, address, and phone) from SQL Table, 
 * 		Result Codes count and percentages,
 * 		Phone Number matches,
 * 		Addresses which do not have an attached Company Name from BusinessCoder,
 * 		Contact last name matches
 * 
 * Program Description: Reads in SQL Table and sends a REST Request to BusinessCoder per each distinct 
 * company location. The program runs the tests specified above in "Outputs" and then outputs the report.
 * 
 * @author Alyssa House
 */

package businessCoder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Main {
	private static Properties properties = new Properties();
	
	private static int distinctSize = 0;
	private static int totalSize = 0;
	
	// Private fields for result codes
	private static int as01 = 0;
	private static int as02 = 0;
	private static int as03 = 0;
	private static int as13 = 0;
	private static int as14 = 0;
	private static int as15 = 0;
	private static int as16 = 0;
	private static int as17 = 0;
	private static int as20 = 0;
	private static int as23 = 0;

	private static int fs01 = 0;
	private static int fs02 = 0;
	private static int fs03 = 0;
	private static int fs04 = 0;
	private static int fs05 = 0;
	private static int fs07 = 0;
	private static int fs08 = 0;
	
	//Private field for null company name counts
	private static int countBusinessCoderNullCompanyName = 0;
	private static int countSqlNullCompanyName = 0;
	
	//Private fields for phone numbers
	private static int countMatchedPhones = 0;
	private static int countDiffPhones = 0;
	private static int countBothNullPhones = 0;
	private static int countNullResponsePhone = 0;
	private static int countNullInputPhone = 0;
	
	//Private fields for contacts
	private static int countMatchedContacts = 0;
	
	//debug
	private static int debugInputContacts = 0;
	
	public static void main(String[] args) {
		String request = "";
		ResultSet resultSet = null;
		String[][] nullCompanyNameAddresses = new String[5000][5]; // Note: size is currently fixed at 1000
		List<String> contactsFromInput = new ArrayList<String>();
		List<String> contactsFromResponse = new ArrayList<String>();
		String[] currentRequestParameters = new String[8];	// format: company name, address line 1,
									// address line 2, city, state, zip, phone, web address
		// Get properties
		try {
			properties.load(new FileInputStream("config.properties"));
		} catch (IOException e) {
			System.out.println("ERROR IOException in main() : "
					+ "Unable to load config.properties.");
			e.printStackTrace();
			System.exit(0);
		}
		
		resultSet = getSqlResultSet();
		
		do {
			currentRequestParameters = getParametersFromSql(resultSet);

			if (currentRequestParameters == null) {
				break;
			}
			
			// Build and send request for result codes
			request = buildRESTRequest(currentRequestParameters);
			sendRequest(request);

			// Get and count result codes
			String resultCodes = "";
			resultCodes = getResultCodes();
			countResults(resultCodes);

			// Get contacts from REST response and SQL input, then intersect them
			contactsFromResponse = getResponseContacts();
			contactsFromInput = getSqlInputContacts(currentRequestParameters);
			intersectContacts((ArrayList<String>) contactsFromInput, (ArrayList<String>) contactsFromResponse);

			// Build and send request for null company name test and phone comparison test
			String phoneFromInput = "";
			
			phoneFromInput = currentRequestParameters[6];
			currentRequestParameters[6] = "";
			currentRequestParameters[0] = "";
			
			request = buildRESTRequest(currentRequestParameters);

			sendRequest(request);

			// Get company name from REST response
			String companyNameFromResponse = "";
			companyNameFromResponse = getCompanyName();

			// Count and store addresses with no company name from BusinessCoder
			if (companyNameFromResponse.equals("")) {
				for (int i = 0; i < 5; i++) {
					nullCompanyNameAddresses[countBusinessCoderNullCompanyName][i] = currentRequestParameters[i + 1];
				}
				countBusinessCoderNullCompanyName++;
			}

			// Build text file containing a list of addresses with no company name
			buildNullCompanyNameAddressesFile(nullCompanyNameAddresses);
			
			// Get REST response phone and compare with SQL input phone
			String phoneFromResponse = "";
			phoneFromResponse = getPhone();

			comparePhones(phoneFromResponse, phoneFromInput);

			// Sum up total entries from input
			distinctSize++;
			System.out.println("Current entry: " + distinctSize);
		} while (currentRequestParameters != null);

		// Get total number of rows from SQL
		totalSize = getSqlTotalRows();
		
		// Get null company name entries from SQL
		countSqlNullCompanyName = getSqlTotalNullCompanyNames();
		
		// Build HTML report
		buildHTML();

		// Clean: remove JSONResponse.json
		try {
			Files.deleteIfExists(Paths.get("JSONResponse.json"));
		} catch (NoSuchFileException e) {
			System.out.println("ERROR NoSuchFileException : Unable to locate JSONResponse.json");
		} catch (IOException e) {
			System.out.println("ERROR IOException : Unable to delete JSONResponse.json");
			e.printStackTrace();
		}
		
		//debug
		System.out.println("Total unused input contacts: " + debugInputContacts);
		
		System.out.println("Program finished successfully.");
	}	
	
	/**
	 * Opens a connection to brown11 SQL Server and executes a query returning the company name,
	 * address line 1, address line 2, city, state, zip, phone number, and web address of each row. 
	 * @return result set from query
	 */
	private static ResultSet getSqlResultSet() {
		//String connectionString = "jdbc:sqlserver:<server>;" + "database=<database>;" + "user=<user>;" + "password=<password>;";
		ResultSet resultSet = null;
		Statement statement = null;
		Connection connection = null;
		
		String phone = "";
		String webAddress = "";

		if (resultSet == null) {
			try {
				if (!properties.getProperty("Phone").equals("")) {
					phone = "," + properties.getProperty("Phone");
				}
				
				if (!properties.getProperty("WebAddress").equals("")) {
					webAddress = "," + properties.getProperty("WebAddress");
				}
				
				connection = DriverManager.getConnection(connectionString);
				String sqlQuery = "SELECT DISTINCT " 
						+ properties.getProperty("CompanyName") + ","
						+ properties.getProperty("Address1") + ","
						+ properties.getProperty("Address2") + ","
						+ properties.getProperty("City") + ","
						+ properties.getProperty("State") + ","
						+ properties.getProperty("Zip")
						+ phone
						+ webAddress
						+ " FROM " + properties.getProperty("SQLTable");
				statement = connection.createStatement();
				resultSet = statement.executeQuery(sqlQuery);
			} catch (SQLException e) {
				System.out.println("ERROR SQLException in getSqlResultSet() : "
						+ "Unable to query for REST Request parameters.");
				e.printStackTrace();
				System.exit(0);
			}
		}
		
		return resultSet;
	}
	
	/**
	 * Opens a connection to brown11 SQL Server and executes a query returning the total number of rows
	 * @return total number of rows in SQL Table
	 */
	private static int getSqlTotalRows() {
		//String connectionString = "jdbc:sqlserver:<server>;" + "database=<database>;" + "user=<user>;" + "password=<password>;";
		ResultSet resultSet = null;
		Statement statement = null;
		Connection connection = null;
		int totalRows = 0;

		if (resultSet == null) {
			try {
				connection = DriverManager.getConnection(connectionString);
				String selectSql = "SELECT COUNT(*) " 
						+ " FROM " + properties.getProperty("SQLTable");			
				statement = connection.createStatement();
				resultSet = statement.executeQuery(selectSql);
				resultSet.next();
				totalRows = resultSet.getInt(1);
			} catch (SQLException e) {
				System.out.println("ERROR SQLException in getSqlTotalRows(): Unable to query for total rows.");
				e.printStackTrace();
				System.exit(0);
			}
		}
		
		return totalRows;
	}
	
	/**
	 * Opens a connection to brown11 SQL Server and executes a query returning the total number of entries
	 * with a null company name
	 * @return number of entries with a null company name
	 */
	private static int getSqlTotalNullCompanyNames() {
		//String connectionString = "jdbc:sqlserver:<server>;" + "database=<database>;" + "user=<user>;" + "password=<password>;";
		ResultSet resultSet = null;
		Statement statement = null;
		Connection connection = null;
		int totalNullCompanyNames = 0;

		if (resultSet == null) {
			try {
				connection = DriverManager.getConnection(connectionString);
				String selectSql = "SELECT COUNT(*)" 
						+ " FROM " + properties.getProperty("SQLTable")
						+ " WHERE " + properties.getProperty("CompanyName") +"=\'\' or " 
						+ properties.getProperty("CompanyName") + "=\'-\'";
				statement = connection.createStatement();
				resultSet = statement.executeQuery(selectSql);
				resultSet.next();
				totalNullCompanyNames = resultSet.getInt(1);
			} catch (SQLException e) {
				System.out.println("ERROR SQLException in getSqlTotalNullCompanyNames(): Unable to query for total null company names.");
				e.printStackTrace();
				System.exit(0);
			}
		}
		return totalNullCompanyNames;
	}
	
	/**
	 * Gets a single row from SQL result set
	 * @param resultSet SQL result set
	 * @return String array containing the REST parameters as follows : company name, address line 1, 
	 * address line 2, city, state, zip, and phone number of each row
	 */
	private static String[] getParametersFromSql(ResultSet resultSet) {
		String[] parameters = new String[8];
		
		try {
			while (resultSet.next()) {
				for (int i = 0; i < 6; i++) {
					parameters[i] = resultSet.getString(i + 1).toString().trim();
				}
				
				if (!properties.getProperty("Phone").equals("")) {
					parameters[6] = resultSet.getString(7).toString().trim();
				} else {
					parameters[6] = "";
				}
				
				if (!properties.getProperty("WebAddress").equals("")) {
					parameters[7] = resultSet.getString(8).toString().trim();
				} else {
					parameters[7] = "";
				}
				return parameters;
			}
		} catch (SQLException e) {
			System.out.println("ERROR SQLException in getParametersFromSql() : "
					+ "Unable to build currLine string");
			e.printStackTrace();
			System.exit(0);
		}

		return null;
	}
	
	/**
	 * Builds the REST Request parameters string for Melissa Data's BusinessCoder Web Service
	 * @param fields String array of parameters in specified format: { , Company Name, Address 
	 * 				 line 1, Address line 2, City, State, Postal Code}
	 * @return REST Request parameters string
	 */
	private static String buildRESTRequest(String[] fields) {
		// Initialize fields for REST request
		String request = "";
		String id = ""; // license key
		String companyName = fields[0]; 		// company name
		String addressLine1 = fields[1]; 		// address line 1
		String addressLine2 = fields[2]; 		// address line 2
		String city = fields[3]; 			// city
		String state = fields[4]; 			// state
		String postal = fields[5]; 			// ZIP code
		String phone = fields[6]; 			// phone number			
		String web = fields[7]; 			// web address
		String cols = "Phone,Contacts";			// column
		String opt = "MaxContacts:500"; 		// options
//		String country = ""; 				// country
//		String transmissionReference = "TEST"; 		// transmission reference
//		String rec = ""; 				// record ID	
//		String mak = ""; 				// Melissa Address Key
//		String stock = ""; 				// stock ticker
//		String mek = ""; 				// Melissa Enterprise Key

		// Build request string
		request = "?id=" + id + "&comp=" + companyName + "&a1=" + addressLine1 + "&a2=" 
				+ addressLine2 + "&city=" + city + "&state=" + state +"&postal=" + postal 
				+ "&phone=" + phone + "&web=" + web +"&cols=" + cols + "&opt=" + opt +"&format=json";
		
		// (Optional) Build full request string
		//request = "?t=" + transmissionReference + "&id=" + id + "&cols=" + cols 
		//+ "&opt=" + "opt" + "&rec=" + rec + "&comp=" + comp + "&a1=" + a1 + 
		//"&a2=" + a2 + "&city=" + city + "&state=" + state + "&postal=" + postal 
		//+ "&ctry=" + country + "&format=json";

		return request;
	}

	/**
	 * Establishes a connection to Melissa Data's BusinessCoder Web Service and creates JSONResponse.json, a document
	 * that holds the current REST Response in JSON format
	 * @param request REST Request parameters string
	 */
	private static void sendRequest(String request)  {
		String httpAddress = "//businesscoder.melissadata.net/WEB/BusinessCoder/doBusinessCoderUS";
		
		// Create URI
		URI uri = null;
		try {
			uri = new URI("http", httpAddress + request, null);
		} catch (URISyntaxException e) {
			System.out.println("ERROR URISyntaxException : Unable to build URI.");
		}

		// Create URL
		URL url = null;
		try {
			url = new URL(uri.toURL().toString());
		} catch (MalformedURLException e1) {
			System.out.println("ERROR MalformedURLException : Unable to build URL.");
		}

		// Open a Connection
		HttpURLConnection urlConn = null;
		try {
			urlConn = (HttpURLConnection) (url.openConnection());
		} catch (IOException e1) {
			System.out.println("ERROR IOException : Unable to open connection to URL.");
		}

		// Read in the JSON response and write it into a file
		BufferedReader jsonResponse = null;
		String readLine = "";
		String jsonString = "";
		FileWriter jsonFile = null;
		try {

			urlConn.connect();
			jsonResponse = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
			jsonFile = new FileWriter("JSONResponse.json");

			while ((readLine = jsonResponse.readLine()) != null) {
				jsonString += readLine + "\n";
			}

			jsonFile.write(jsonString);
			jsonFile.flush();
			jsonFile.close();
			jsonResponse.close();
			urlConn.disconnect();
		} catch (IOException e1) {
			System.out.println("ERROR IOException : Unable to read in JSON response and write to JSONResponse.json.");
			e1.printStackTrace();
			System.exit(0);
		}
	}
	
	/**
	 * Returns a string containing the result codes received from the most recent REST Response from BusinessCoder
	 * @return string containing result codes separated by commas
	 */
	private static String getResultCodes() {
		String resultCodes = "";
		FileReader fileReader = null;
		BufferedReader bufferedReader = null;
		JSONObject jsonResponseObj = null;
		JSONObject recordsObj = null;
		JSONArray records;

		// Create jsonResponseObj from JSON file
		try {
			fileReader = new FileReader("JSONResponse.json");
			bufferedReader = new BufferedReader(fileReader);
			jsonResponseObj = (JSONObject) new JSONParser().parse(bufferedReader);
			
			bufferedReader.close();
			fileReader.close();
		} catch (IOException | ParseException e) {
			System.out.println("ERROR : IOException or ParseException : Unable to create JSONObject"
					+ " from parsing JSONResponse.json.");
			e.printStackTrace();
		} 

		// Create subset of jsonResponseObj containing the pairs in the Records array
		records = ((JSONArray) jsonResponseObj.get("Records"));
		recordsObj = (JSONObject) records.get(0);

		// Get result codes
		resultCodes = recordsObj.get("Results").toString();

		return resultCodes;
	}

	/**
	 * Returns a string containing the company name received from the most recent REST Response from BusinessCoder
	 * @return string containing the company name
	 */
	private static String getCompanyName() {
		String companyName = "";
		FileReader fileReader = null;
		BufferedReader bufferedReader = null;
		JSONObject jsonResponseObj = null;
		JSONObject recordsObj = null;
		JSONArray records;

		// Create jsonResponseObj from JSON file
		try {
			fileReader = new FileReader("JSONResponse.json");
			bufferedReader = new BufferedReader(fileReader);
			jsonResponseObj = (JSONObject) new JSONParser().parse(bufferedReader);
			
			bufferedReader.close();
			fileReader.close();
		} catch (IOException | ParseException e) {
			System.out.println("ERROR : IOException or ParseException : Unable to create JSONObject"
					+ " from parsing JSONResponse.json.");
			e.printStackTrace();
		} 

		// Create subset of jsonResponseObj containing the pairs in the Records array
		records = ((JSONArray) jsonResponseObj.get("Records"));
		recordsObj = (JSONObject) records.get(0);
		
		// Get company name
		companyName = "" + recordsObj.get("CompanyName").toString();

		return companyName;
	}
	
	/**
	 * Opens a connection to brown11 SQL Server and executes a query returning the total number of distinct
	 * last names per each distinct company location
	 * @return the total number of distinct last names per each distinct company location
	 */
	private static int getTotalDistinctInputContacts() {
		int totalDistinctInputContacts = 0;
		//String connectionString = "jdbc:sqlserver:<server>;" + "database=<database>;" + "user=<user>;" + "password=<password>;";
		Statement statement = null;
		Connection connection = null;
		ResultSet contactsResultSet = null;
		
		String phone = "";
		String webAddress = "";
		
		if (!properties.getProperty("Phone").isEmpty()) {
			phone = "," + properties.getProperty("Phone");
		}
		
		if (!properties.getProperty("WebAddress").isEmpty()) {
			webAddress = "," + properties.getProperty("WebAddress");
		}
		
		String propertyAddress2 = "";
		
		if (!(properties.getProperty("Address2").equals("\'\' as placeholder"))) {
			propertyAddress2 = properties.getProperty("Address2") + ", ";
		}
		
		try {
			connection = DriverManager.getConnection(connectionString);
			String selectSql = "SELECT SUM(Contacts) FROM " 
					+ "  (SELECT DISTINCT COUNT(DISTINCT "
					+ properties.getProperty("ContactLastName") + ") as Contacts,"
					+ properties.getProperty("CompanyName") + ", " + properties.getProperty("Address1") + ", " 
					+ propertyAddress2 + properties.getProperty("City") + ", " 
					+ properties.getProperty("State") + ", " + properties.getProperty("Zip") + phone + webAddress + "  FROM "
					+ properties.getProperty("SQLTable") 
					+ "  GROUP BY " 
					+ properties.getProperty("CompanyName") + ", " 
					+ properties.getProperty("Address1") + ", " + propertyAddress2
					+ properties.getProperty("City") + ", " + properties.getProperty("State") + ", " + properties.getProperty("Zip")
					+ phone + webAddress + ") AS ContactsPerCompanyLocation";
			statement = connection.createStatement();
			contactsResultSet = statement.executeQuery(selectSql);
		} catch (SQLException e) {
			System.out.println("ERROR SQLException in getTotalDistinctInputContacts() : "
					+ "Unable to execute query.");
			System.exit(0);
		}

		try {
			while (contactsResultSet.next()) {
				totalDistinctInputContacts = contactsResultSet.getInt(1);
			}
		} catch (SQLException e) {
			System.out.println("ERROR SQLException in getTotalDistinctInputContacts() : "
					+ "Unable to add contact to List.");
		}
		
		return totalDistinctInputContacts;
	}
	/**
	 * Returns a string array containing the last names of the contacts from the most recent REST Response 
	 * from BusinessCoder
	 * @return string array containing the contacts' last names
	 */
	private static List<String> getResponseContacts() {
		List<String> returnedContacts = new ArrayList<String>();
		FileReader fileReader = null;
		BufferedReader bufferedReader = null;
		JSONObject jsonResponseObj = null;
		JSONObject recordsObj = null;
		JSONObject contactsObj = null;
		JSONArray records;
		JSONArray contacts;

		// Create jsonResponseObj from JSON file
		try {
			fileReader = new FileReader("JSONResponse.json");
			bufferedReader = new BufferedReader(fileReader);
			jsonResponseObj = (JSONObject) new JSONParser().parse(bufferedReader);
			
			bufferedReader.close();
			fileReader.close();
		} catch (IOException | ParseException e) {
			System.out.println("ERROR : IOException or ParseException : Unable to create JSONObject"
					+ " from parsing JSONResponse.json.");
			e.printStackTrace();
		} 

		// Create subset of jsonResponseObj containing the pairs in the Records array
		records = ((JSONArray) jsonResponseObj.get("Records"));
		recordsObj = (JSONObject) records.get(0);

		// Create subset of recordsObj containing the pairs in the Contacts array
		contacts = ((JSONArray) recordsObj.get("Contacts"));

		for (int i = 0; i < contacts.size(); i++) {
			contactsObj = (JSONObject) contacts.get(i);

			// Get contact's last name
			returnedContacts.add(contactsObj.get("NameLast").toString());
		}
		
		// Return string array of contacts' last names
		returnedContacts.sort(null);	
		return returnedContacts;
	}
	
	/**
	 * Returns list of contacts from a distinct Company Location
	 * @param parameters string array containing company name, address line 1, address line 2, city, state, zip,
	 * and phone in that specific order
	 * @return ArrayList<String> containing the contacts from the SQL input for the given entry
	 */
	private static List<String> getSqlInputContacts(String[] parameters) {
		List<String> returnedContacts = new ArrayList<String>();
		//String connectionString = "jdbc:sqlserver:<server>;" + "database=<database>;" + "user=<user>;" + "password=<password>;";
		Statement statement = null;
		Connection connection = null;
		ResultSet contactsResultSet = null;

		String companyName = parameters[0];
		String address = parameters[1];
		String address2 = parameters[2];
		String city = parameters[3];
		String state = parameters[4];
		String zip = parameters[5];

		// If company name contains an apostrophe, correct format
		if (companyName.contains("\'")) {
			companyName = companyName.replaceAll("\'", "\'\'");
		}
		
		if (address.contains("\'")) {
			address = address.replaceAll("\'", "\'\'");
		}
		
		String propertyAddress2 = "";
		
		if (!(properties.getProperty("Address2").equals("\'\' as placeholder"))) {
			propertyAddress2 = " AND " + properties.getProperty("Address2") + " = \'" + address2 + "\' ";
		}
				
		try {
			connection = DriverManager.getConnection(connectionString);
			String selectSql = "SELECT distinct " + properties.getProperty("ContactLastName") 
					+ " FROM  " + properties.getProperty("SQLTable") 
					+ " WHERE " + properties.getProperty("CompanyName") + "=\'" + companyName + "\' "
					+ " AND " + properties.getProperty("Address1") + " = \'" + address + "\' "
					+   propertyAddress2
					+ " AND " + properties.getProperty("City") + " = \'" + city + "\' "
					+ " AND " + properties.getProperty("State") + " = \'" + state + " \' "
					+ " AND " + properties.getProperty("Zip") + " = \'" + zip + "\'";			
			statement = connection.createStatement();
			contactsResultSet = statement.executeQuery(selectSql);
		} catch (SQLException e) {
			System.out.println("ERROR SQLException in getSqlInputContacts() : "
					+ "Unable to execute query.");
			e.printStackTrace();
			System.exit(0);
		}

		try {
			while (contactsResultSet.next()) {
				String lastName = contactsResultSet.getString(1).toString().trim();		
				if (properties.getProperty("FullName").equals("true")) {
					if (lastName.contains(" ")) {
						lastName = lastName.substring(lastName.lastIndexOf(" ")).trim();
					}
				}
				returnedContacts.add(lastName);
			}
		} catch (SQLException e) {
			System.out.println("ERROR SQLException in getSqlInputContacts () : "
					+ "Unable to add contact to List.");
		}
		
		returnedContacts.sort(null);
		return returnedContacts;
	}
	
	/**
	 * Returns a string containing the phone number received from the most recent REST Response from BusinessCoder
	 * @return string containing the phone number
	 */
	private static String getPhone() {
		String phone = "";
		FileReader fileReader = null;
		BufferedReader bufferedReader = null;
		JSONObject jsonResponseObj = null;
		JSONObject recordsObj = null;
		JSONArray records;

		// Create jsonResponseObj from JSON file
		try {
			fileReader = new FileReader("JSONResponse.json");
			bufferedReader = new BufferedReader(fileReader);
			jsonResponseObj = (JSONObject) new JSONParser().parse(bufferedReader);
		} catch (IOException | ParseException e) {
			System.out.println("ERROR : IOException or ParseException : Unable to create JSONObject"
					+ " from parsing JSONResponse.json.");
			e.printStackTrace();
		} finally {
			if (fileReader != null) {
				try {
					bufferedReader.close();
					fileReader.close();
				} catch (IOException e) {
					System.out.println("ERROR IOException in getPhone() : Unable to close file reader.");
					e.printStackTrace();
				}
			}
		}

		// Create subset of jsonResponseObj containing the pairs in the Records array
		records = ((JSONArray) jsonResponseObj.get("Records"));
		recordsObj = (JSONObject) records.get(0);
		
		// Get company name
		phone = "" + recordsObj.get("Phone").toString();

		return phone;
	}
	
	/**
	 * Compares the phone number from the input file with the phone number from the REST response
	 * @param responsePhone phone number from REST response
	 * @param inputPhone phone number from input file
	 */
	private static void comparePhones(String responsePhone, String inputPhone) {
		
		if (responsePhone.trim().isEmpty() && inputPhone.trim().isEmpty()) {
			countBothNullPhones++;
		} else if (responsePhone.isEmpty()) {
			countNullResponsePhone++;
		} else if (inputPhone.isEmpty()) {
			countNullInputPhone++;
		} else if (responsePhone.equals(inputPhone)) {
			countMatchedPhones++;
		} else {
			countDiffPhones++;
		}
	}
	
	/**
	 * Compares the contacts' last names from the SQL input with the contact's last names from the
	 * REST response. Adds matched contacts to countMatchedContacts
	 * @param inputContacts String ArrayList containing the last names of the contacts from the SQL 
	 * input for a specific company location
	 * @param responseContacts String ArrayList containing the last names of the contacts from the 
	 * BusinessCoder's REST Response
	 */
	private static void intersectContacts(ArrayList<String> inputContacts, ArrayList<String> responseContacts) {
		List<String> matchedContacts = new ArrayList<String>();
		
		for (String inputContact : inputContacts) {
			if (responseContacts.contains(inputContact)) {
				matchedContacts.add(inputContact);
			}
		}
		
		// Remove shared contacts from each list
		for (String sharedContact : matchedContacts) {
			responseContacts.remove(sharedContact);
			inputContacts.remove(sharedContact);	
		}
		//DEBUG
		debugInputContacts += responseContacts.size();
		
		// Update matched contacts
		countMatchedContacts += matchedContacts.size();
	}
	
	/**
	 * Builds text file named nullCompanyNameAddresses.txt which contains a list of addresses from input that
	 * do not have a returned company name from BusinessCoder
	 * @param addresses 2D String array with list of addresses that have a null company name
	 */
	private static void buildNullCompanyNameAddressesFile(String[][] addresses) {
		FileWriter fileWriter = null;
		BufferedWriter bufferedWriter = null;
		
		try {
			fileWriter = new FileWriter("Null-Company-Name-Addresses.txt");
			bufferedWriter = new BufferedWriter(fileWriter);
			
			bufferedWriter.write("Addresses with Null Company Name in BusinessCoder\n");
			for (int i = 0; i < addresses.length; i++) {
						if (addresses[i][0] != null) {
							for (int j = 0; j < addresses[i].length; j++) {
								bufferedWriter.write(addresses[i][j] + " ");
							}
							bufferedWriter.write("\n");;
						}
					}
			
			bufferedWriter.close();
			fileWriter.close();
		} catch (IOException e) {
			System.out.println("ERROR IOException : Unable to create FileWriter for Null-Company-Name-Addresses.txt.");
		}
	}
	
	/**
	 * Sums up each of the result codes
	 * @param resultCodes string containing result codes separated by commas
	 */
 	private static void countResults(String resultCodes) {
		String[] arrCodes = resultCodes.split(",");

		// Add current result codes from last REST Response to running sum for the entire input file
		for (String str : arrCodes) {
			if (str.contains("AS")) {
				
				if (str.equals("AS01")) 	 {as01++;} 
				else if (str.equals("AS02")) {as02++;} 
				else if (str.equals("AS03")) {as03++;} 
				else if (str.equals("AS13")) {as13++;}
				else if (str.equals("AS14")) {as14++;}
				else if (str.equals("AS15")) {as15++;} 
				else if (str.equals("AS16")) {as16++;} 
				else if (str.equals("AS17")) {as17++;} 
				else if (str.equals("AS20")) {as20++;} 
				else if (str.equals("AS23")) {as23++;} 

			} else if (str.contains("FS")) {
				
				if (str.equals("FS01")) 	 {fs01++;} 
				else if (str.equals("FS02")) {fs02++;} 
				else if (str.equals("FS03")) {fs03++;} 
				else if (str.equals("FS04")) {fs04++;} 
				else if (str.equals("FS05")) {fs05++;} 
				else if (str.equals("FS07")) {fs07++;} 
				else if (str.equals("FS08")) {fs08++;}

			}
		}
	}

	/**
	 * Builds HTML file based on htmlInput.html template and updates the information with the 
	 * calculations/tests from this program
	 */
	private static void buildHTML()  {
		File htmlFileIn;
		File htmlFileOut;
		String htmlString = "";
		
		// Read in htmlInput.html to htmlString
		htmlFileIn = new File("htmlInput.html");
		htmlFileOut = new File("htmlOutput.html");
		try {
			htmlString = FileUtils.readFileToString(htmlFileIn, "UTF-8");
		} catch (IOException e) {
			System.out.println("ERROR IOException : Unable to read in htmlInput.html.");
		}

		htmlString = htmlString.replace("$totalSize", "" + totalSize);
		htmlString = htmlString.replace("$distinctSize", "" + distinctSize);
		
		// Update Null Company Name table in htmlOutput.html
		htmlString = htmlString.replace("$countBusinessCoderNullCompanyName", "" + countBusinessCoderNullCompanyName);
		htmlString = htmlString.replace("$percentageBusinessCoderNullCompanyName",
				"" + round(((double) countBusinessCoderNullCompanyName / distinctSize * 100), 2));
		htmlString = htmlString.replace("$countSqlNullCompanyName", "" + countSqlNullCompanyName);
		htmlString = htmlString.replace("$percentageSqlNullCompanyName",
				"" + round(((double) countSqlNullCompanyName / totalSize * 100), 2));

		// Update Company Contacts table in htmlOutput.html
		htmlString = htmlString.replace("$countMatchedContacts", "" + countMatchedContacts);
		htmlString = htmlString.replace("$totalContacts", "" + getTotalDistinctInputContacts());
		htmlString = htmlString.replace("$percentageMatchedContacts",
				"" + round(((double) countMatchedContacts / getTotalDistinctInputContacts() * 100), 2));
		
		// Update Result Code Count in htmlOutput.html
		htmlString = htmlString.replaceFirst(Pattern.quote("$as01"), "" + as01);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as02"), "" + as02);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as03"), "" + as03);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as13"), "" + as13);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as14"), "" + as14);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as15"), "" + as15);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as16"), "" + as16);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as17"), "" + as17);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as20"), "" + as20);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as23"), "" + as23);
	
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs01"), "" + fs01);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs02"), "" + fs02);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs03"), "" + fs03);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs04"), "" + fs04);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs05"), "" + fs05);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs07"), "" + fs07);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs08"), "" + fs08);
		
		// Calculate Result Code Distinct Percentages
		double percentageDistinctAS01 = round(( (double) as01/distinctSize * 100), 2);
		double percentageDistinctAS02 = round(( (double) as02/distinctSize * 100), 2);
		double percentageDistinctAS03 = round(( (double) as03/distinctSize * 100), 2);
		double percentageDistinctAS13 = round(( (double) as13/distinctSize * 100), 2);
		double percentageDistinctAS14 = round(( (double) as14/distinctSize * 100), 2);
		double percentageDistinctAS15 = round(( (double) as15/distinctSize * 100), 2);
		double percentageDistinctAS16 = round(( (double) as16/distinctSize * 100), 2);
		double percentageDistinctAS17 = round(( (double) as17/distinctSize * 100), 2);
		double percentageDistinctAS20 = round(( (double) as20/distinctSize * 100), 2);
		double percentageDistinctAS23 = round(( (double) as23/distinctSize * 100), 2);
		
		double percentageDistinctFS01 = round(( (double) fs01/distinctSize * 100), 2);
		double percentageDistinctFS02 = round(( (double) fs02/distinctSize * 100), 2);
		double percentageDistinctFS03 = round(( (double) fs03/distinctSize * 100), 2);
		double percentageDistinctFS04 = round(( (double) fs04/distinctSize * 100), 2);
		double percentageDistinctFS05 = round(( (double) fs05/distinctSize * 100), 2);
		double percentageDistinctFS07 = round(( (double) fs07/distinctSize * 100), 2);
		double percentageDistinctFS08 = round(( (double) fs08/distinctSize * 100), 2);
		
		// Update Result Code Distinct Percentages in htmlOutput.html
		htmlString = htmlString.replaceFirst(Pattern.quote("$as01"), "" + percentageDistinctAS01);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as02"), "" + percentageDistinctAS02);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as03"), "" + percentageDistinctAS03);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as13"), "" + percentageDistinctAS13);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as14"), "" + percentageDistinctAS14);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as15"), "" + percentageDistinctAS15);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as16"), "" + percentageDistinctAS16);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as17"), "" + percentageDistinctAS17);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as20"), "" + percentageDistinctAS20);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as23"), "" + percentageDistinctAS23);
	
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs01"), "" + percentageDistinctFS01);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs02"), "" + percentageDistinctFS02);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs03"), "" + percentageDistinctFS03);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs04"), "" + percentageDistinctFS04);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs05"), "" + percentageDistinctFS05);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs07"), "" + percentageDistinctFS07);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs08"), "" + percentageDistinctFS08);
		
		// Calculate Result Code Total Percentages
		double percentageTotalAS01 = round(((double) as01 / totalSize * 100), 2);
		double percentageTotalAS02 = round(((double) as02 / totalSize * 100), 2);
		double percentageTotalAS03 = round(((double) as03 / totalSize * 100), 2);
		double percentageTotalAS13 = round(((double) as13 / totalSize * 100), 2);
		double percentageTotalAS14 = round(((double) as14 / totalSize * 100), 2);
		double percentageTotalAS15 = round(((double) as15 / totalSize * 100), 2);
		double percentageTotalAS16 = round(((double) as16 / totalSize * 100), 2);
		double percentageTotalAS17 = round(((double) as17 / totalSize * 100), 2);
		double percentageTotalAS20 = round(((double) as20 / totalSize * 100), 2);
		double percentageTotalAS23 = round(((double) as23 / totalSize * 100), 2);

		double percentageTotalFS01 = round(((double) fs01 / totalSize * 100), 2);
		double percentageTotalFS02 = round(((double) fs02 / totalSize * 100), 2);
		double percentageTotalFS03 = round(((double) fs03 / totalSize * 100), 2);
		double percentageTotalFS04 = round(((double) fs04 / totalSize * 100), 2);
		double percentageTotalFS05 = round(((double) fs05 / totalSize * 100), 2);
		double percentageTotalFS07 = round(((double) fs07 / totalSize * 100), 2);
		double percentageTotalFS08 = round(((double) fs08 / totalSize * 100), 2);

		// Update Result Code Total Percentages in htmlOutput.html
		htmlString = htmlString.replaceFirst(Pattern.quote("$as01"), "" + percentageTotalAS01);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as02"), "" + percentageTotalAS02);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as03"), "" + percentageTotalAS03);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as13"), "" + percentageTotalAS13);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as14"), "" + percentageTotalAS14);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as15"), "" + percentageTotalAS15);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as16"), "" + percentageTotalAS16);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as17"), "" + percentageTotalAS17);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as20"), "" + percentageTotalAS20);
		htmlString = htmlString.replaceFirst(Pattern.quote("$as23"), "" + percentageTotalAS23);

		htmlString = htmlString.replaceFirst(Pattern.quote("$fs01"), "" + percentageTotalFS01);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs02"), "" + percentageTotalFS02);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs03"), "" + percentageTotalFS03);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs04"), "" + percentageTotalFS04);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs05"), "" + percentageTotalFS05);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs07"), "" + percentageTotalFS07);
		htmlString = htmlString.replaceFirst(Pattern.quote("$fs08"), "" + percentageTotalFS08);

		// Update Phone table
		htmlString = htmlString.replace("$countMatchedPhones", "" + countMatchedPhones);
		htmlString = htmlString.replace("$percentageDistinctMatchedPhones",
				"" + round(((double) countMatchedPhones / distinctSize * 100), 2));
		htmlString = htmlString.replace("$percentageTotalMatchedPhones",
				"" + round(((double) countMatchedPhones / totalSize * 100), 2));

		htmlString = htmlString.replace("$countDiffPhones", "" + countDiffPhones);
		htmlString = htmlString.replace("$percentageDistinctDiffPhones",
				"" + round(((double) countDiffPhones / distinctSize * 100), 2));
		htmlString = htmlString.replace("$percentageTotalDiffPhones",
				"" + round(((double) countDiffPhones / totalSize * 100), 2));
		
		htmlString = htmlString.replace("$countBothNullPhones", "" + countBothNullPhones);
		htmlString = htmlString.replace("$percentageDistinctBothNullPhones",
				"" + round(((double) countBothNullPhones / distinctSize * 100), 2));
		htmlString = htmlString.replace("$percentageTotalBothNullPhones",
				"" + round(((double) countBothNullPhones / totalSize * 100), 2));

		htmlString = htmlString.replace("$countNullResponsePhone", "" + countNullResponsePhone);
		htmlString = htmlString.replace("$percentageDistinctNullResponsePhone",
				"" + round(((double) countNullResponsePhone / distinctSize * 100), 2));
		htmlString = htmlString.replace("$percentageTotalNullResponsePhone",
				"" + round(((double) countNullResponsePhone / totalSize * 100), 2));

		htmlString = htmlString.replace("$countNullInputPhone", "" + countNullInputPhone);
		htmlString = htmlString.replace("$percentageDistinctNullInputPhone",
				"" + round(((double) countNullInputPhone / distinctSize * 100), 2));
		htmlString = htmlString.replace("$percentageTotalNullInputPhone",
				"" + round(((double) countNullInputPhone / totalSize * 100), 2));
		

		// Update background colors for percentages in htmlOutput.html
		//Temporary: don't worry about colors for now, just set them all to white
		htmlString = htmlString.replace("$bg", "ffffff");
		
		/*
		// AS codes
		if (pAS01 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS01 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pAS02 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS02 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pAS03 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS03 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		
		if (pAS13 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS13 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pAS14 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS14 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pAS15 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS15 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pAS16 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS16 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pAS17 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS17 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pAS20 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS20 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pAS23 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pAS23 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		// FS codes
		if (pFS01 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pFS01 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pFS02 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pFS02 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pFS03 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pFS03 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pFS04 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pFS04 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pFS05 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pFS05 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pFS07 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pFS07 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		
		if (pFS08 > 70) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ccffcc");
		} else if (pFS08 < 30) {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ff5050");
		} else {
			htmlString = htmlString.replaceFirst(Pattern.quote("$bg"), "#ffffff");
		}
		*/

		try {
			FileUtils.writeStringToFile(htmlFileOut, htmlString, "UTF-8");
		} catch (IOException e) {
			System.out.println("ERROR IOException : Unable to write htmlOutput.html.");
		}
	}
	
	/**
	 * Helper method that rounds a double value to numDigits decimal placements
	 * @param value double value to be rounded
	 * @param numDigits number of placement digits after the decimal point
	 * @return Rounded double with specified placement digits
	 */
	public static double round(double value, int numDigits) {
		if (numDigits < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(numDigits, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}

}
