/**
 * Assign a MAK id to Verified Global Addresses Project
 * WriteToPropertiesFile.java
 * 
 * Program description: writes config.properties file which contains the properties for
 * this project. Currently the only property is table, which defines the SQL Table to be
 * used as input and updated with a mak ID
 */


package businessCoder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

public class WriteToPropertiesFile {

	public static void main (String[] args) {
		Properties prop = new Properties();
		OutputStream output = null;

		try {

			output = new FileOutputStream("config.properties");

			// table 1 Properties
			/*
			prop.setProperty("SQLTable", ""); //set table
			prop.setProperty("CompanyName", "company");
			prop.setProperty("Address1", "address");
			prop.setProperty("Address2", "address2");
			prop.setProperty("City", "city");
			prop.setProperty("State", "state");
			prop.setProperty("Zip", "zip");
			prop.setProperty("Phone", "telephone_number");
			prop.setProperty("WebAddress", "web_address");
			prop.setProperty("ContactLastName", "name_last");
			*/
			
			// table 2 Properties
			/*
			prop.setProperty("SQLTable", ""); //set table
			prop.setProperty("CompanyName", "name");
			prop.setProperty("Address1", "address");
			prop.setProperty("Address2", "\'\' as placeholder");
			prop.setProperty("City", "city");
			prop.setProperty("State", "state");
			prop.setProperty("Zip", "zipCode");
			prop.setProperty("Phone", "phone");
			prop.setProperty("WebAddress", "");
			prop.setProperty("ContactLastName", "ContactLastName1");
			*/

			// table 3 Properties
			prop.setProperty("SQLTable", ""); //set  table
			prop.setProperty("CompanyName", "Company");
			prop.setProperty("Address1", "Address");
			prop.setProperty("Address2", "Address2");
			prop.setProperty("City", "City");
			prop.setProperty("State", "State");
			prop.setProperty("Zip", "Zip");
			prop.setProperty("Phone", "PhoneContact");
			prop.setProperty("WebAddress", "WebAddress");
			prop.setProperty("ContactLastName", "Name");
			prop.setProperty("FullName", "true");
			
			// save properties to project root folder
			prop.store(output, null);

		} catch (IOException io) {
			io.printStackTrace();
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}
		System.out.println("config.properties written.");
	}

}
