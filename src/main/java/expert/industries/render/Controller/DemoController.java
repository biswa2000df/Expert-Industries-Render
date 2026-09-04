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
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class DemoController {

    private static final String API_URL =
            "https://renderfirstproject.onrender.com/api/sendMail";

    private static final String EVERY_5_MIN_API_URL =
            "https://renderfirstproject.onrender.com/api/Welcome";

    private static final String UPLOAD_DIR =
            System.getProperty("user.dir") + File.separator;

    private final RestTemplate restTemplate = new RestTemplate();

    /*
     * These values are stored after the WorkingHourCount API is called.
     *
     * Note:
     * This works for your current single-user flow.
     * For multiple simultaneous users, these should not be static.
     */
    public static String uploadedFileName = "";
    public static String employeeName = "";

    public static double totalMonthlyWorkingHours;
    public static double perHourSalary;
    public static double totalSalaryPerMonth;

    public static double afterMISandHolidayHrs_TotalCalculationHrs;
    public static double afterMIS_FinalWorkHrs;
    public static double beforeAdvanceCalculation_TotalSalary;
    public static double afterAllCalculationCompleted_TotalSalary;

    private static boolean workingHourCalculationCompleted = false;

    @Autowired
    private JavaMailSender mailSender;

    private final String mailBody =
            "Dear Team,\n\n" +
            "This is a friendly reminder to ensure that you punch in " +
            "when you arrive at the office and punch out before you leave " +
            "for the day.\n\n" +
            "Thank you for your cooperation!\n\n" +
            "Best regards,\n" +
            "Biswajit Sahoo\n" +
            "QA Engineer\n" +
            "Mahindra & Mahindra Financial Services Limited";

    // ================================================================
    // BASIC APIs
    // ================================================================

    @GetMapping("/api/Welcome")
    @Operation(summary = "Welcome Data")
    public String hello() {
        return "Welcome to Expert Industries";
    }

    @GetMapping("/api/greet")
    @Operation(summary = "Check your Name")
    public String greet(@RequestParam String name) {
        return "Hello, My Dear " + name + "!";
    }


    // ================================================================
    // FILE UPLOAD API
    // ================================================================

    @Operation(
            summary = "Upload a file",
            description = "Uploads an Excel file to the server"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "File uploaded successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid file",
                    content = @Content(
                            schema = @Schema(hidden = true)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            schema = @Schema(hidden = true)
                    )
            )
    })
    @PostMapping(
            value = "/api/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file) {

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity
                        .badRequest()
                        .body("Please select a file to upload.");
            }

            String originalFilename = file.getOriginalFilename();

            if (originalFilename == null ||
                    originalFilename.trim().isEmpty()) {

                return ResponseEntity
                        .badRequest()
                        .body("Invalid file name.");
            }

            /*
             * This removes directory information from the supplied
             * filename and helps prevent path traversal.
             */
            String safeFilename = Paths.get(originalFilename)
                    .getFileName()
                    .toString();

            if (!safeFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                return ResponseEntity
                        .badRequest()
                        .body("Only .xlsx Excel files are supported.");
            }

            Path uploadDirectory = Paths.get(UPLOAD_DIR)
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(uploadDirectory);

            Path destinationPath = uploadDirectory
                    .resolve(safeFilename)
                    .normalize();

            if (!destinationPath.startsWith(uploadDirectory)) {
                return ResponseEntity
                        .badRequest()
                        .body("Invalid upload file path.");
            }

            Files.copy(
                    file.getInputStream(),
                    destinationPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            uploadedFileName = safeFilename;

            /*
             * Reset old calculation values whenever a new file
             * is uploaded.
             */
            resetCalculationValues();

            return ResponseEntity.ok(
                    "File uploaded successfully: " + safeFilename
            );

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("File upload failed: " + e.getMessage());
        }
    }

    // ================================================================
    // FILE DOWNLOAD API
    // ================================================================

    @GetMapping("/download/{filename:.+}")
    @Operation(summary = "Download Excel Sheet File")
    public ResponseEntity<byte[]> downloadExcel(
            @PathVariable String filename) {

        try {
            Path filePath = getSafeFilePath(filename);

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            byte[] fileBytes = Files.readAllBytes(filePath);

            HttpHeaders headers = new HttpHeaders();

            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(filePath.getFileName().toString())
                            .build()
            );

            headers.setContentType(
                    MediaType.APPLICATION_OCTET_STREAM
            );

            headers.setContentLength(fileBytes.length);

            return new ResponseEntity<>(
                    fileBytes,
                    headers,
                    HttpStatus.OK
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(null);

        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    // ================================================================
    // FILE DELETE API
    // ================================================================

    @Operation(
            summary = "Delete a file",
            description = "Deletes a specified file from the server"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "File deleted successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "File not found",
                    content = @Content(
                            schema = @Schema(hidden = true)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            schema = @Schema(hidden = true)
                    )
            )
    })
    @DeleteMapping("/api/delete/{filename:.+}")
    public ResponseEntity<String> deleteFile(
            @PathVariable String filename) {

        try {
            Path filePath = getSafeFilePath(filename);

            boolean deleted = Files.deleteIfExists(filePath);

            if (!deleted) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("File not found: " + filename);
            }

            if (filename.equals(uploadedFileName)) {
                uploadedFileName = "";
                resetCalculationValues();
            }

            return ResponseEntity.ok(
                    "File deleted successfully: " + filename
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());

        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not delete file: " + e.getMessage());
        }
    }

    // ================================================================
    // LIST FILES API
    // ================================================================

    @Operation(
            summary = "List all files",
            description = "Retrieves all files in the upload directory"
    )
    @GetMapping("/api/listOfFiles")
    public ResponseEntity<List<String>> listFiles() {

        List<String> fileNames = new ArrayList<>();

        try {
            Path uploadDirectory = Paths.get(UPLOAD_DIR)
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(uploadDirectory);

            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(uploadDirectory)) {

                for (Path path : stream) {
                    if (!Files.isDirectory(path)) {
                        String fileName =
                                path.getFileName().toString();

                        if (!fileName.equalsIgnoreCase("app.jar")) {
                            fileNames.add(fileName);
                        }
                    }
                }
            }

            Collections.sort(fileNames);

            if (fileNames.isEmpty()) {
                return ResponseEntity.ok(
                        Collections.singletonList(
                                "No uploaded files are available."
                        )
                );
            }

            return ResponseEntity.ok(fileNames);

        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(
                            "Unable to retrieve files: " + e.getMessage()
                    ));
        }
    }

    // ================================================================
    // WORKING HOURS CALCULATION API
    // ================================================================

    @GetMapping("/api/MotiExcelSheet/WorkingHourCount")
    @Operation(summary = "Moti Excel Sheet Working Hour Count")
    public synchronized ResponseEntity<String> motiExcelSheet() {

        if (uploadedFileName == null ||
                uploadedFileName.trim().isEmpty()) {

            return ResponseEntity
                    .badRequest()
                    .body("Please upload an Excel file first.");
        }

        Path excelFilePath;

        try {
            excelFilePath = getSafeFilePath(uploadedFileName);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }

        if (!Files.exists(excelFilePath)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Uploaded Excel file was not found.");
        }

        try (FileInputStream fileInputStream =
                     new FileInputStream(excelFilePath.toFile());

             XSSFWorkbook workbook =
                     new XSSFWorkbook(fileInputStream)) {

            XSSFSheet sheet = workbook.getSheet("Sheet1");

            if (sheet == null) {
                return ResponseEntity
                        .badRequest()
                        .body("Sheet1 was not found in the Excel file.");
            }

            Row firstRow = sheet.getRow(0);

            if (firstRow == null) {
                return ResponseEntity
                        .badRequest()
                        .body("The first row is empty.");
            }

            /*
             * Your existing code processes row number 5.
             * Apache POI row index starts from zero, so index 4
             * represents Excel row 5.
             */
            Row attendanceRow = sheet.getRow(4);

            if (attendanceRow == null) {
                return ResponseEntity
                        .badRequest()
                        .body(
                                "Attendance data was not found in Excel row 5."
                        );
            }

            XSSFCellStyle normalStyle =
                    createNormalCellStyle(workbook);

            XSSFCellStyle misStyle =
                    createMisCellStyle(workbook);

            ArrayList<String> firstRowValues =
                    readRowValues(firstRow);

            /*
             * This method sets employeeName and returns the
             * employee's hourly salary.
             */
            perHourSalary =
                    readEmpNameAndCalculateSalary(firstRowValues);

            List<String> updatedTimes = new ArrayList<>();

            int lastAttendanceColumn =
                    attendanceRow.getLastCellNum();

            if (lastAttendanceColumn <= 1) {
                return ResponseEntity
                        .badRequest()
                        .body(
                                "No attendance values were found in Excel row 5."
                        );
            }

            /*
             * Begin with column index 1 because column zero
             * is not attendance data in your original code.
             */
            for (int columnIndex = 1;
                 columnIndex < lastAttendanceColumn;
                 columnIndex++) {

                Cell attendanceCell = attendanceRow.getCell(
                        columnIndex,
                        Row.MissingCellPolicy.CREATE_NULL_AS_BLANK
                );

                String cellValue =
                        getCellValue(attendanceCell);

                String updatedTime =
                        checkAndGetUpdatedTime(cellValue);

                updatedTimes.add(updatedTime);
            }

            if (updatedTimes.isEmpty()) {
                return ResponseEntity
                        .badRequest()
                        .body("No working-hour records were found.");
            }

            int summaryRowIndex =
                    sheet.getLastRowNum() + 1;

            Row workingHoursSummaryRow =
                    sheet.createRow(summaryRowIndex);

            int totalMinutes = 0;

            for (int index = 0;
                 index < updatedTimes.size();
                 index++) {

                int excelColumnIndex = index + 1;

                String updatedTime =
                        updatedTimes.get(index);

                Cell outputCell =
                        workingHoursSummaryRow.createCell(
                                excelColumnIndex
                        );

                if ("MIS".equalsIgnoreCase(updatedTime)) {
                    outputCell.setCellValue("0.00");
                    outputCell.setCellStyle(misStyle);
                    continue;
                }

                outputCell.setCellValue(updatedTime);
                outputCell.setCellStyle(normalStyle);

                totalMinutes += convertDurationToMinutes(
                        updatedTime
                );
            }

            /*
             * Correct payroll conversion.
             *
             * Example:
             * 40 hours 30 minutes = 40.5 hours
             * It must not be treated as 40.30 hours.
             */
            totalMonthlyWorkingHours = totalMinutes / 60.0;

            totalSalaryPerMonth =
                    totalMonthlyWorkingHours * perHourSalary;

            int totalHoursColumn = lastAttendanceColumn;
            int totalSalaryColumn = lastAttendanceColumn + 1;

            Cell totalHoursCell =
                    workingHoursSummaryRow.createCell(
                            totalHoursColumn
                    );

            totalHoursCell.setCellValue(
                    totalMonthlyWorkingHours
            );

            totalHoursCell.setCellStyle(normalStyle);

            Cell salaryCell =
                    workingHoursSummaryRow.createCell(
                            totalSalaryColumn
                    );

            salaryCell.setCellValue(
                    totalSalaryPerMonth
            );

            salaryCell.setCellStyle(normalStyle);

            workingHourCalculationCompleted = true;

            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(
                                 excelFilePath.toFile()
                         )) {

                workbook.write(fileOutputStream);
            }

            return ResponseEntity.ok(
                    "Working hours updated successfully. " +
                    "Employee: " + employeeName +
                    ", Per-hour salary: " +
                    formatMoney(perHourSalary) +
                    ", Total working hours: " +
                    formatNumber(totalMonthlyWorkingHours) +
                    ", Salary before adjustments: " +
                    formatMoney(totalSalaryPerMonth)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body("Validation error: " + e.getMessage());

        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            "Unable to process Excel file: " +
                            e.getMessage()
                    );

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            "Unexpected error while calculating hours: " +
                            e.getMessage()
                    );
        }
    }

    // ================================================================
    // CELL VALUE READER
    // ================================================================

    private static String getCellValue(Cell cell) {

        if (cell == null) {
            return "";
        }

        DataFormatter formatter = new DataFormatter();

        return formatter
                .formatCellValue(cell)
                .trim();
    }

    private static ArrayList<String> readRowValues(Row row) {

        ArrayList<String> rowValues = new ArrayList<>();

        if (row == null) {
            return rowValues;
        }

        int lastColumn = row.getLastCellNum();

        for (int columnIndex = 0;
             columnIndex < lastColumn;
             columnIndex++) {

            Cell cell = row.getCell(
                    columnIndex,
                    Row.MissingCellPolicy.CREATE_NULL_AS_BLANK
            );

            rowValues.add(getCellValue(cell));
        }

        return rowValues;
    }

    // ================================================================
    // DAILY DURATION CALCULATION
    // ================================================================

    private static String checkAndGetUpdatedTime(
            String cellValue) {

        if (cellValue == null ||
                cellValue.trim().isEmpty()) {

            /*
             * Blank cells are treated as zero working time.
             */
            return "0.00";
        }

        String[] lines =
                cellValue.trim().split("\\R");

        if (lines.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid attendance cell value: \"" +
                    cellValue +
                    "\". Expected duration and attendance status."
            );
        }

        String attendanceStatus =
                lines[lines.length - 1].trim();

        String totalTime =
                lines[lines.length - 2].trim();

        if (attendanceStatus.equalsIgnoreCase("MIS")) {
            return "MIS";
        }

        String[] timeParts =
                totalTime.split(":");

        if (timeParts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid duration format: " +
                    totalTime +
                    ". Expected H:mm format."
            );
        }

        int hours;
        int minutes;

        try {
            hours = Integer.parseInt(
                    timeParts[0].trim()
            );

            minutes = Integer.parseInt(
                    timeParts[1].trim()
            );

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid numeric duration: " + totalTime
            );
        }

        if (hours < 0 ||
                minutes < 0 ||
                minutes > 59) {

            throw new IllegalArgumentException(
                    "Invalid duration: " + totalTime
            );
        }

        int totalMinutes =
                (hours * 60) + minutes;

        /*
         * Deduct 30 minutes only for Present status.
         *
         * Math.max prevents this error:
         * 00:28 - 30 minutes becoming 23:58.
         *
         * Current rule:
         * 28 minutes - 30 minutes = 0 minutes.
         */
        if (attendanceStatus.equalsIgnoreCase("P")) {
            totalMinutes =
                    Math.max(0, totalMinutes - 30);
        }

        int updatedHours =
                totalMinutes / 60;

        int updatedMinutes =
                totalMinutes % 60;

        return String.format(
                Locale.ROOT,
                "%d.%02d",
                updatedHours,
                updatedMinutes
        );
    }

    private static int convertDurationToMinutes(
            String duration) {

        if (duration == null ||
                duration.trim().isEmpty() ||
                duration.equalsIgnoreCase("MIS")) {

            return 0;
        }

        String[] parts =
                duration.trim().split("\\.");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid calculated duration: " + duration
            );
        }

        int hours;
        int minutes;

        try {
            hours = Integer.parseInt(parts[0]);
            minutes = Integer.parseInt(parts[1]);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid calculated duration: " + duration
            );
        }

        if (hours < 0 ||
                minutes < 0 ||
                minutes > 59) {

            throw new IllegalArgumentException(
                    "Invalid calculated duration: " + duration
            );
        }

        return (hours * 60) + minutes;
    }

    // ================================================================
    // EMPLOYEE SALARY CALCULATION
    // ================================================================

    public static Double readEmpNameAndCalculateSalary(
            ArrayList<String> firstRowValues) {

        String employeeNameFormat = null;

        for (String firstRowValue : firstRowValues) {

            if (firstRowValue == null) {
                continue;
            }

            String normalizedValue =
                    firstRowValue
                            .trim()
                            .toLowerCase(Locale.ROOT);

            if (normalizedValue.contains("emp name") &&
                    firstRowValue.contains(":")) {

                employeeNameFormat = firstRowValue;
                break;
            }
        }

        if (employeeNameFormat == null) {
            throw new IllegalArgumentException(
                    "Employee name was not found in the first row. " +
                    "Expected format: Emp Name : Employee Name"
            );
        }

        String[] employeeNameParts =
                employeeNameFormat.split(":", 2);

        if (employeeNameParts.length < 2 ||
                employeeNameParts[1].trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Invalid employee name format: " +
                    employeeNameFormat
            );
        }

        /*
         * Store employee name in normalized lowercase format.
         */
        employeeName = employeeNameParts[1]
                .trim()
                .toLowerCase(Locale.ROOT);

        Map<String, Double> salaryMap =
                createEmployeeSalaryMap();

        Double salary =
                salaryMap.get(employeeName);

        if (salary == null) {
            throw new IllegalArgumentException(
                    "Per-hour salary is not configured for employee: " +
                    employeeName
            );
        }

        System.out.println(
                "Employee Name: " + employeeName
        );

        System.out.println(
                "Per Hour Salary: " + salary
        );

        return salary;
    }

    private static Map<String, Double>
    createEmployeeSalaryMap() {

        Map<String, Double> salaryMap =
                new HashMap<>();

        /*
         * Keep all employee names in lowercase.
         * The extracted Excel employee name is also lowercase.
         */
        salaryMap.put("dadasaheb kolhe", 118.75);
        salaryMap.put("gajanan raut", 90.00);
        salaryMap.put("bhagyavendra singh", 100.00);
        salaryMap.put("salim mohameed", 106.25);
        salaryMap.put("alim", 93.75);
        salaryMap.put("mahindra", 93.75);

        return salaryMap;
    }

    // ================================================================
    // ADVANCE AND FINAL SALARY API
    // ================================================================

    @Operation(
            summary = "Advance Calculation",
            description = "Final advance and salary calculation"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Advance calculation completed",
                    content = @Content(mediaType = "text/plain")
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request",
                    content = @Content(
                            schema = @Schema(hidden = true)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            schema = @Schema(hidden = true)
                    )
            )
    })
    @PostMapping("/AdvanceSalaryCalculation")
    public synchronized ResponseEntity<String> processData(
            @RequestBody AdvanceCalculation advanceCalculation) {

        if (!workingHourCalculationCompleted) {
            return ResponseEntity
                    .badRequest()
                    .body(
                            "Please execute WorkingHourCount API " +
                            "before AdvanceSalaryCalculation API."
                    );
        }

        if (advanceCalculation == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Advance calculation request is required.");
        }

        double holidayHours =
                advanceCalculation.getHolidayHrs();

        double misHours =
                advanceCalculation.getMisHrs();

        double advanceSalary =
                advanceCalculation.getAdvance();

        if (holidayHours < 0) {
            return ResponseEntity
                    .badRequest()
                    .body("Holiday hours cannot be negative.");
        }

        if (misHours < 0) {
            return ResponseEntity
                    .badRequest()
                    .body("MIS hours cannot be negative.");
        }

        if (advanceSalary < 0) {
            return ResponseEntity
                    .badRequest()
                    .body("Advance salary cannot be negative.");
        }

        try {
            afterMISandHolidayHrs_TotalCalculationHrs =
                    holidayHours + misHours;

            afterMIS_FinalWorkHrs =
                    totalMonthlyWorkingHours +
                    afterMISandHolidayHrs_TotalCalculationHrs;

            beforeAdvanceCalculation_TotalSalary =
                    afterMIS_FinalWorkHrs *
                    perHourSalary;

            afterAllCalculationCompleted_TotalSalary =
                    beforeAdvanceCalculation_TotalSalary -
                    advanceSalary;

            /*
             * If advance is greater than calculated salary,
             * prevent a negative payable salary.
             *
             * If your business rule allows negative balances,
             * remove this Math.max line.
             */
            afterAllCalculationCompleted_TotalSalary =
                    Math.max(
                            0,
                            afterAllCalculationCompleted_TotalSalary
                    );

            finalSalaryUpdate(
                    holidayHours,
                    misHours,
                    advanceSalary
            );

            return ResponseEntity.ok(
                    "Final salary updated successfully. " +
                    "Employee: " + employeeName +
                    ", Per-hour salary: " +
                    formatMoney(perHourSalary) +
                    ", Final working hours: " +
                    formatNumber(afterMIS_FinalWorkHrs) +
                    ", Final salary: " +
                    formatMoney(
                            afterAllCalculationCompleted_TotalSalary
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body("Validation error: " + e.getMessage());

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            "Final salary calculation failed: " +
                            e.getMessage()
                    );
        }
    }

    // ================================================================
    // FINAL SALARY EXCEL UPDATE
    // ================================================================

    public static void finalSalaryUpdate(
            double holidayHours,
            double misHours,
            double advanceSalary) throws IOException {

        if (uploadedFileName == null ||
                uploadedFileName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "No uploaded Excel file is available."
            );
        }

        if (employeeName == null ||
                employeeName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Employee name is not available."
            );
        }

        Path excelFilePath =
                getSafeFilePath(uploadedFileName);

        try (FileInputStream fileInputStream =
                     new FileInputStream(excelFilePath.toFile());

             XSSFWorkbook workbook =
                     new XSSFWorkbook(fileInputStream)) {

            XSSFSheet sheet =
                    workbook.getSheet("Sheet1");

            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Sheet1 was not found in the Excel file."
                );
            }

            XSSFCellStyle style =
                    createFinalSummaryStyle(workbook);

            List<String> columnNameList =
                    getColumnNameList();

            List<Object> columnValueList =
                    getColumnValueList(
                            holidayHours,
                            misHours,
                            advanceSalary
                    );

            if (columnNameList.size() !=
                    columnValueList.size()) {

                throw new IllegalStateException(
                        "Column name count and column value count " +
                        "do not match."
                );
            }

            /*
             * Add one blank row after the existing data.
             */
            int headerRowIndex =
                    sheet.getLastRowNum() + 2;

            int dataRowIndex =
                    headerRowIndex + 1;

            Row headerRow =
                    sheet.createRow(headerRowIndex);

            Row dataRow =
                    sheet.createRow(dataRowIndex);

            for (int columnIndex = 0;
                 columnIndex < columnNameList.size();
                 columnIndex++) {

                Cell headerCell =
                        headerRow.createCell(columnIndex);

                headerCell.setCellValue(
                        columnNameList.get(columnIndex)
                );

                headerCell.setCellStyle(style);

                Cell dataCell =
                        dataRow.createCell(columnIndex);

                Object value =
                        columnValueList.get(columnIndex);

                setCellValue(dataCell, value);

                dataCell.setCellStyle(style);

                sheet.autoSizeColumn(columnIndex);
            }

            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(
                                 excelFilePath.toFile()
                         )) {

                workbook.write(fileOutputStream);
            }
        }
    }

    // ================================================================
    // FINAL EXCEL COLUMN NAMES
    // ================================================================

    private static List<String> getColumnNameList() {

        List<String> columnNameList =
                new ArrayList<>();

        /*
         * New columns requested by you.
         */
        columnNameList.add("Employee_Name");
        columnNameList.add("Per_Hour_Salary");

        /*
         * Existing calculation columns.
         */
        columnNameList.add("Before_TotalWorkHrs");
        columnNameList.add("Before_TotalSalary");
        columnNameList.add("Holiday_Hrs");
        columnNameList.add("MIS_Hrs");
        columnNameList.add("MIS_Plus_Holiday_Hrs");
        columnNameList.add("FinalWorkHrs");
        columnNameList.add("BeforeAdvanceCalculation_Salary");
        columnNameList.add("Advance_Salary");
        columnNameList.add("FinalSalary");

        return columnNameList;
    }

    // ================================================================
    // FINAL EXCEL COLUMN VALUES
    // ================================================================

    private static List<Object> getColumnValueList(
            double holidayHours,
            double misHours,
            double advanceSalary) {

        List<Object> columnValueList =
                new ArrayList<>();

        /*
         * New values requested by you.
         */
        columnValueList.add(employeeName);
        columnValueList.add(perHourSalary);

        /*
         * Existing calculation values.
         */
        columnValueList.add(totalMonthlyWorkingHours);
        columnValueList.add(totalSalaryPerMonth);
        columnValueList.add(holidayHours);
        columnValueList.add(misHours);

        columnValueList.add(
                afterMISandHolidayHrs_TotalCalculationHrs
        );

        columnValueList.add(
                afterMIS_FinalWorkHrs
        );

        columnValueList.add(
                beforeAdvanceCalculation_TotalSalary
        );

        columnValueList.add(advanceSalary);

        columnValueList.add(
                afterAllCalculationCompleted_TotalSalary
        );

        return columnValueList;
    }

    // ================================================================
    // EXCEL STYLE METHODS
    // ================================================================

    private static XSSFCellStyle createNormalCellStyle(
            XSSFWorkbook workbook) {

        XSSFCellStyle style =
                workbook.createCellStyle();

        style.setAlignment(
                HorizontalAlignment.CENTER
        );

        XSSFFont font =
                workbook.createFont();

        font.setBold(true);
        font.setFontName("Times New Roman");
        font.setItalic(true);
        font.setColor(
                IndexedColors.BLACK.getIndex()
        );

        style.setFont(font);

        return style;
    }

    private static XSSFCellStyle createMisCellStyle(
            XSSFWorkbook workbook) {

        XSSFCellStyle style =
                workbook.createCellStyle();

        style.setAlignment(
                HorizontalAlignment.CENTER
        );

        /*
         * setFillForegroundColor should be used with
         * SOLID_FOREGROUND.
         */
        style.setFillForegroundColor(
                IndexedColors.RED.getIndex()
        );

        style.setFillPattern(
                FillPatternType.SOLID_FOREGROUND
        );

        XSSFFont font =
                workbook.createFont();

        font.setBold(true);
        font.setFontName("Times New Roman");
        font.setItalic(true);
        font.setColor(
                IndexedColors.BLACK.getIndex()
        );

        style.setFont(font);

        return style;
    }

    private static XSSFCellStyle createFinalSummaryStyle(
            XSSFWorkbook workbook) {

        XSSFCellStyle style =
                workbook.createCellStyle();

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        style.setAlignment(
                HorizontalAlignment.CENTER
        );

        XSSFFont font =
                workbook.createFont();

        font.setBold(true);
        font.setFontName("Times New Roman");
        font.setItalic(true);

        font.setColor(
                IndexedColors.BLACK.getIndex()
        );

        style.setFont(font);

        return style;
    }

    // ================================================================
    // COMMON HELPER METHODS
    // ================================================================

    private static void setCellValue(
            Cell cell,
            Object value) {

        if (value == null) {
            cell.setCellValue("");
            return;
        }

        if (value instanceof String) {
            cell.setCellValue((String) value);
            return;
        }

        if (value instanceof Number) {
            cell.setCellValue(
                    ((Number) value).doubleValue()
            );
            return;
        }

        if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
            return;
        }

        cell.setCellValue(value.toString());
    }

    private static Path getSafeFilePath(
            String filename) {

        if (filename == null ||
                filename.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "File name cannot be empty."
            );
        }

        String safeFilename =
                Paths.get(filename)
                        .getFileName()
                        .toString();

        Path uploadDirectory =
                Paths.get(UPLOAD_DIR)
                        .toAbsolutePath()
                        .normalize();

        Path filePath =
                uploadDirectory
                        .resolve(safeFilename)
                        .normalize();

        if (!filePath.startsWith(uploadDirectory)) {
            throw new IllegalArgumentException(
                    "Invalid file path."
            );
        }

        return filePath;
    }

    private static void resetCalculationValues() {

        employeeName = "";

        totalMonthlyWorkingHours = 0;
        perHourSalary = 0;
        totalSalaryPerMonth = 0;

        afterMISandHolidayHrs_TotalCalculationHrs = 0;
        afterMIS_FinalWorkHrs = 0;
        beforeAdvanceCalculation_TotalSalary = 0;
        afterAllCalculationCompleted_TotalSalary = 0;

        workingHourCalculationCompleted = false;
    }

    private static String formatMoney(
            double amount) {

        return String.format(
                Locale.ROOT,
                "%.2f",
                amount
        );
    }

    private static String formatNumber(
            double number) {

        return String.format(
                Locale.ROOT,
                "%.2f",
                number
        );
    }
}
