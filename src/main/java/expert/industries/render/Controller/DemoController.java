package expert.industries.render.Controller;

import expert.industries.render.Entity.AdvanceCalculation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


@RestController
@CrossOrigin(origins = "*")
public class DemoController {

    @GetMapping("/api/Welcome")
    @Operation(summary = "Welcome Data")
    public String hello() {
        return "Welcome to Expert Industries";
    }



    // Second API: Greet with a name
    @GetMapping("/api/greet")
    @Operation(summary = "Check your Name")
    public String greet(@RequestParam String name) {
        return "Hello, My Dear " + name + "!";
    }



    private static final String API_URL = "https://renderfirstproject.onrender.com/api/sendMail";
    private static final String Every_5MIN_API_URL = "https://renderfirstproject.onrender.com/api/Welcome";
    private final RestTemplate restTemplate = new RestTemplate();

    @Scheduled(cron = "0 30 3 * * *") // Every day at 9 AM
    public void callApiAt9AM() {
        callApi();
    }

    @Scheduled(cron = "0 30 4 * * *") // Every day at 10 AM
    public void callApiAt10AM() {
        callApi();
    }

    @Scheduled(cron = "0 30 11 * * *") // Every day at 5 PM
    public void callApiAt5PM() {
        callApi();
    }

    @Scheduled(cron = "0 30 12 * * *") // Every day at 6 PM
    public void callApiAt6PM() {
        callApi();
    }


//    @Scheduled(cron = "0 */10 * * * *") //
//    public void callApiAtEvery5MIN() {
//        continuousCallApi();
//git status
    

    public void continuousCallApi() {
        try {
            String response = restTemplate.getForObject(Every_5MIN_API_URL, String.class);
//            System.out.println("API Response: " + response);
        } catch (Exception e) {
            System.err.println("Error calling API: " + e.getMessage());
        }
    }

    public void callApi() {
        try {
            String response = restTemplate.getForObject(API_URL, String.class);
            System.out.println("API Response: " + response);
        } catch (Exception e) {
            System.err.println("Error calling API: " + e.getMessage());
        }
    }

    String mailBody = "Dear Team,\n" +
            "\n" +
            "This is a friendly reminder to ensure that you punch in when you arrive at the office and punch out before you leave for the day.\n" +
            "\n" +
            "Thank you for your cooperation!\n" +
            "\n" +
            "Best regards,\n" +
            "Biswajit sahoo\n" +
            "QA Engineer\n" +
            "Mahindra & Mahindra Financial Services Limited";

    @Autowired
    private JavaMailSender mailSender;

    @GetMapping("/api/sendMail")
    @Operation(summary = "Mail Send")
    public String sendEmail() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("sahoo.biswajit@mahindra.com", "namratashete38@gmail.com", "deotare.sandhya@mahfin.com");
        message.setSubject("Reminder: Punch In and Out for Attendance Compliance");
        message.setText(mailBody);

        mailSender.send(message);
        return "Mail Send Successfully";
    }



    @GetMapping("/download/{filename:.+}") // Accept any filename including those with dots
    @Operation(summary = "Download ExcelSheet File")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable String filename) {
        File file = new File(System.getProperty("user.dir") + File.separator + filename); // Specify the directory where files are stored
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null); // Return 404 if the file does not exist
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            byte[] bytes = inputStream.readAllBytes(); // Read the file into a byte array
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", file.getName()); // Set the content disposition for download
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM); // Set the content type
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK); // Return the response entity
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Handle the error
        }
    }


    public static String uploadedFileName = "";
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator ; // Set the upload directory

    @Operation(summary = "Upload a file", description = "Uploads a file to the server")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(hidden = true)))
    })
    @PostMapping(value = "/api/upload", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<String> uploadFile(@RequestParam("file")
            MultipartFile file)
    {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a file to upload");
            }

            // Save the file locally
            Path path = Paths.get(UPLOAD_DIR + file.getOriginalFilename());
            Files.createDirectories(path.getParent()); // Create directories if not exists
            Files.write(path, file.getBytes());

            uploadedFileName = file.getOriginalFilename();
            return ResponseEntity.ok("File uploaded successfully: " + file.getOriginalFilename());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload failed: " + e.getMessage());
        }
    }

    @Operation(summary = "Delete a file", description = "Deletes a specified file from the server")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File deleted successfully"),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(hidden = true)))
    })
    @DeleteMapping("/api/delete/{filename:.+}")
    public ResponseEntity<String> deleteFile(@PathVariable String filename) {
        try {
            Path path = Paths.get(UPLOAD_DIR + filename);
            Files.deleteIfExists(path);
            return ResponseEntity.ok("File deleted successfully: " + filename);
        } catch (NoSuchFileException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found: " + filename);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Could not delete file: " + e.getMessage());
        }
    }


    @Operation(summary = "List all files", description = "Retrieves a list of all files in the upload directory")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Files retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(hidden = true)))
    })
    @GetMapping("/api/listOfFiles")
    public ResponseEntity<List<String>> listFiles() {
        List<String> fileNames = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(UPLOAD_DIR))) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    fileNames.add(path.getFileName().toString());
                }
            }

            boolean isPresent = fileNames.stream().anyMatch(s -> s.equals("app.jar"));

            if(fileNames.size() == 1 &&  fileNames.get(0).equalsIgnoreCase("app.jar")) {
                return ResponseEntity.ok(Collections.singletonList("One .jar file is available."));
            }else if(isPresent){
                fileNames.remove("app.jar");
                return ResponseEntity.ok(fileNames);
            }else {
                return ResponseEntity.ok(fileNames);
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }



    public static double totalMonthlyWorkingMinutes;
    public static double perHourSalary;
    public static double totalSalaryPerMonth;


    @GetMapping("/api/MotiExcelSheet/WorkingHourCount")
    @Operation(summary = "MotiExcelSheet WorkingHourCount")
    public String MotiExcelsheet() {

        String excelFilePath = System.getProperty("user.dir") + File.separator + uploadedFileName;
        try (FileInputStream fis = new FileInputStream(excelFilePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            XSSFCellStyle styleMIS = workbook.createCellStyle();
            styleMIS.setAlignment(HorizontalAlignment.CENTER);
            styleMIS.setFillBackgroundColor(IndexedColors.RED.getIndex());
            styleMIS.setFillPattern(FillPatternType.FINE_DOTS);

            XSSFCellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);


            XSSFFont font = workbook.createFont();
            font.setBold(true);
            font.setFontName("Times New Roman");
            font.setItalic(true);
            font.setColor(IndexedColors.BLACK.getIndex()); // Set font color to black
            styleMIS.setFont(font);
            style.setFont(font);

            // Access the desired sheet
            XSSFSheet sheet = workbook.getSheet("Sheet1");

            List<String> updatedTime = new ArrayList<String>();

            int i=0;//for column count

            ArrayList<String> firstRowValue = new ArrayList<>();

            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                //read only for employee name
                if (rowIndex == 0) {
                    for (Cell cell : row) {
                        String cellValue = getCellValues(cell);
                        firstRowValue.add(cellValue);
                    }
                }


                if (rowIndex < 4) {
                    continue;
                }

                for (Cell cell : row) {
                    i++;
                    // Print cell values based on the cell type
                    String cellValue =  getCellValues(cell);
//                    System.out.println(cellValue);
                    if (cell.getColumnIndex() != 0) {
                        updatedTime.add(checkAndGetUpdatedTime(cellValue));
                    }
                }
                System.out.println("********** Move to the next line after each row ***********"); // Move to the next line after each row


                int lastRowNum = sheet.getLastRowNum();
//                System.out.println("lastrow number = " + lastRowNum);
                Row newRow = sheet.createRow(lastRowNum + 1);

                //create cell
                for(int k = 1; k<i; k++) {
                    newRow.createCell(k);
                    if(updatedTime.get(k-1).equalsIgnoreCase("MIS") ) {
                        newRow.getCell(k).setCellValue("0.00");
                        newRow.getCell(k).setCellStyle(styleMIS);
                    }else {
                        newRow.getCell(k).setCellValue(updatedTime.get(k-1));  //here update the time value
                        newRow.getCell(k).setCellStyle(style);
                    }
                }

                Collections.replaceAll(updatedTime, "MIS", "0.00");

                /////start calculation hour and min//////

//                double totalMonthlyWorkingMinutes;

                int totalMinutes = 0;
                for (String time : updatedTime) {
                    String[] parts = time.split("\\.");
                    int hours = Integer.parseInt(parts[0]);
                    int minutes = Integer.parseInt(parts[1]);

                    totalMinutes += hours * 60 + minutes;
                }

                int totalHours = totalMinutes / 60;
                int remainingMinutes = totalMinutes % 60;
//                System.out.println("Total Time: " + totalHours + " hours and " + remainingMinutes + " minutes");

                /////end calculation hour and min//////

                totalMonthlyWorkingMinutes = Double.parseDouble(totalHours + "." + remainingMinutes);

//               System.out.println("Sum of numbers: " + totalMonthlyWorkingMinutes);
//
//                System.out.println("first row Value = " + firstRowValue);
                 perHourSalary = readEmpNameAndCalculateSalary(firstRowValue);
//                System.out.println("Per Hour Salary = " + perHourSalary);
                 totalSalaryPerMonth = totalMonthlyWorkingMinutes * perHourSalary;
//                System.out.println("total month  Salary = " + totalSalaryPerMonth);

                newRow.createCell(i).setCellValue(totalMonthlyWorkingMinutes);
                newRow.createCell(i+1).setCellValue(totalSalaryPerMonth);


                try (FileOutputStream fileOut = new FileOutputStream(excelFilePath)) {
                    workbook.write(fileOut);
                }
//                System.out.println(updatedTime);
//                System.out.println("Excel file updated successfully!");
                break;

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "Total Hours updated successfully!";

    }


    private static String  getCellValues(Cell cell){
        String cellValue = "";
        switch (cell.getCellType()) {
            case STRING:
                cellValue = cell.getStringCellValue();

                break;
            case NUMERIC:
                cellValue = String.valueOf(cell.getNumericCellValue());

                break;
            case BOOLEAN:
                cellValue = String.valueOf(cell.getBooleanCellValue());

                break;
            default:
                System.out.print("Unknown\t");
        }

        return cellValue;
    }


    private static String checkAndGetUpdatedTime(String cellValue){

        ArrayList<String> al = new ArrayList<String>();

        String beforespace = "";

        for(int i = 0; i< cellValue.length(); i++){
            if(cellValue.charAt(i) == '\n' )
            {
                al.add(beforespace);
                beforespace = "";
            }else{
                beforespace = beforespace + cellValue.charAt(i);
            }
        }

        if (!beforespace.isEmpty()) {
            al.add(beforespace);
        }

        String presentAndAbsent = al.get(al.size()-1);
        String totalTime = al.get(al.size()-2);

        // Define formatter to parse and format the time in "H:mm" format
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm");

        // Parse the string to LocalTime
        LocalTime time = LocalTime.parse(totalTime, formatter);

        if(presentAndAbsent.equalsIgnoreCase("MIS")){
            return "MIS";
        }

        if(presentAndAbsent.equalsIgnoreCase("P")) {
            // Check if the time is less than 12:00 (noon)
            if (time.isBefore(LocalTime.NOON)) {
                // Subtract 30 minutes
                time = time.minusMinutes(30);
            }
        }
        // Convert back to string for display
        String updatedTimeString = time.format(formatter);

//        System.out.println("Updated Time: " + updatedTimeString);
        return updatedTimeString.replaceAll(":",".");
    }

    public static Double readEmpNameAndCalculateSalary(ArrayList<String> firstRowValue) {
        String EmpNameFormat = null;
        String Name = null;
        double perHourSalary = 0;

        for (int i = 0; i < firstRowValue.size(); i++) {
            if (firstRowValue.get(i).contains("Emp Name :")) {
                EmpNameFormat = firstRowValue.get(i);
                break;
            }
        }
        System.out.println(EmpNameFormat);

        Name = EmpNameFormat.split(":")[1].trim();
        System.out.println(Name);

        Map<String, Double> map = new HashMap<>();
        map.put("Dadasaheb kolhe", 118.75);
        map.put("Gajanan Raut", 81.25);
        map.put("Bhagyavendra singh", 100.00);
        map.put("Salim Mohameed", 93.75);
        map.put("alim", 93.75);
        map.put("Mahindra", 93.75);

        




        for (Map.Entry<String, Double> entry : map.entrySet()) {
            if (entry.getKey().contains(Name)) {
                perHourSalary = entry.getValue();
                break;
            }
        }
        return perHourSalary;
    }




    public static double afterMISandHolidayHrs_TotalCalculationHrs;
    public static double afterMIS_FinalWorkHrs;
    public static double beforeAdvanceCalculation_TotalSalary;
    public static double afterAllCalculationCompleted_TotalSalary;

        @Operation(summary = "Advance Calculation ", description = "Final Advance and Salary Calculation")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Advance Calculation successfully", content = @Content(mediaType = "text/plain")),
            @ApiResponse(responseCode = "400", description = "Bad request", content = @Content(schema = @Schema(hidden = true))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(schema = @Schema(hidden = true)))
        })
        @PostMapping("/AdvanceSalaryCalculation")
        public String processData(@RequestBody AdvanceCalculation advanceCalculation) throws Exception {
            // Access the fields from the request body
            double holidayHrs = advanceCalculation.getHolidayHrs();
            double misHrs = advanceCalculation.getMisHrs();
            double advance = advanceCalculation.getAdvance();


            afterMISandHolidayHrs_TotalCalculationHrs = holidayHrs + misHrs;
            afterMIS_FinalWorkHrs = totalMonthlyWorkingMinutes + afterMISandHolidayHrs_TotalCalculationHrs;
            beforeAdvanceCalculation_TotalSalary = afterMIS_FinalWorkHrs * perHourSalary;
            afterAllCalculationCompleted_TotalSalary = beforeAdvanceCalculation_TotalSalary - advance;

            finalSalaryUpdate(holidayHrs, misHrs, advance);//call file to create a new column for final salary

           return "Total time and Hours are updated successfully...";
//            return "afterAllCalculationCompleted_TotalSalary = " + afterAllCalculationCompleted_TotalSalary + " beforeAdvanceCalculation_TotalSalary = " + beforeAdvanceCalculation_TotalSalary + " afterMIS_FinalWorkHrs = " + afterMIS_FinalWorkHrs + " afterMISandHolidayHrs_TotalCalculationHrs = " + afterMISandHolidayHrs_TotalCalculationHrs;
        }




    public static void finalSalaryUpdate(double holidayHrs, double misHrs, double advance) throws Exception{


        String excelFilePath = System.getProperty("user.dir") + File.separator + uploadedFileName;
        FileInputStream fis = new FileInputStream(excelFilePath);
        XSSFWorkbook workbook = new XSSFWorkbook(fis);

        XSSFCellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
//        style.setFillBackgroundColor(IndexedColors.GREEN.getIndex());
//        style.setFillPattern(FillPatternType.FINE_DOTS);

        XSSFFont font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Times New Roman");
        font.setItalic(true);
        font.setColor(IndexedColors.BLACK.getIndex()); // Set font color to black
        style.setFont(font);

            // Access the desired sheet
        XSSFSheet sheet = workbook.getSheet("Sheet1");

        List<String> columnNameList = getColumnNameList();




        int lastRowNum = sheet.getLastRowNum();
//                System.out.println("lastrow number = " + lastRowNum);
        Row headerRow = sheet.createRow(lastRowNum + 12);

        for (int k = 0; k < columnNameList.size(); k++) {
            Cell headerCell = headerRow.createCell(k);
            headerCell.setCellValue(columnNameList.get(k));
            headerCell.setCellStyle(style); // Apply style to header cells
//            sheet.autoSizeColumn(k); // Auto-size column
        }
            //create cell header
//        for(int k = 0; k<columnNameList.size(); k++) {
//            newRow.createCell(k).setCellValue(columnNameList.get(k));
//        }

        List<Double> columnValueList = getColumnValueList(holidayHrs, misHrs, advance);
        Row dataRow = sheet.createRow(lastRowNum + 13);

        // Data Row (column values)
        for (int k = 0; k < columnValueList.size(); k++) {
            Cell dataCell = dataRow.createCell(k);
            dataCell.setCellValue(columnValueList.get(k));
            dataCell.setCellStyle(style); // Apply style to data cells
//            sheet.autoSizeColumn(k); // Auto-size column
        }

        //create cell value
//        for(int k = 0; k<columnNameList.size(); k++) {
//            dataRow.createCell(k).setCellValue(columnValueList.get(k));
//        }

//            newRow.createCell(i).setCellValue(totalMonthlyWorkingMinutes);
//            newRow.createCell(i+1).setCellValue(totalSalaryPerMonth);


            try (FileOutputStream fileOut = new FileOutputStream(excelFilePath)) {
                workbook.write(fileOut);
            }catch (Exception e){

            }
    }

    private static List<String> getColumnNameList() {
        List<String> columnNameList = new ArrayList<String>();
        columnNameList.add("before_TotalWorkHrs");
        columnNameList.add("before_TotalSalary");
        columnNameList.add("Holiday_Hrs");
        columnNameList.add("MIS_Hrs");
        columnNameList.add("MIS + Holiday");
        columnNameList.add("FinalWorkHrs");
        columnNameList.add("beforeAdvCal_Sal");
        columnNameList.add("Adv_Salary");
        columnNameList.add("FinalSalary");
        return columnNameList;
    }

    private static List<Double> getColumnValueList(double Holiday_Hrs, double MIS_Hrs, double Advance_Salary) {
        List<Double> columnNameList = new ArrayList<Double>();
        columnNameList.add(totalMonthlyWorkingMinutes);
        columnNameList.add(totalSalaryPerMonth);
        columnNameList.add(Holiday_Hrs);
        columnNameList.add(MIS_Hrs);
        columnNameList.add(afterMISandHolidayHrs_TotalCalculationHrs);
        columnNameList.add(afterMIS_FinalWorkHrs);
        columnNameList.add(beforeAdvanceCalculation_TotalSalary);
        columnNameList.add(Advance_Salary);
        columnNameList.add(afterAllCalculationCompleted_TotalSalary);
        return columnNameList;
    }
}

