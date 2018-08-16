# BusinessCoder Check Against New Data Sets
### Written by Alyssa House


Inputs: SQL table which contains the records to be processed
<br/>
Outputs: HTML file named "htmlOutput.html" which displays tables of the results


**Project Description:**
The goal of this project is the analyze whether a new set of data for the BusinessCoder Web Application is worth buying. 
The User runs BusinessCoderBatchProcessing.java, and the program will send one REST Request per each data entry. It then takes
the Result Codes from the REST Response and sums them together. The program outputs a report on the Result Codes that came back,
including a tally and percentage.

Source code:
1. htmlInput.html -- HTML template for output
2. Main.java -- main class
3. WriteToPropertiesFile.java -- class that writes to config.properties to select which SQL table to run as input
