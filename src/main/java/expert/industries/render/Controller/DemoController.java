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
import org.apache.poi.ss.util.CellRangeAddress;

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
    private static final String SOURCE_SHEET_NAME = "Sheet1";

private static final String PRINT_SHEET_NAME =
        "Working_Hours_Print";

private static final String WORKING_SUMMARY_MARKER =
        "CALCULATED_WORKING_HOURS";

private static final String FINAL_SUMMARY_MARKER =
        "FINAL_SALARY_SUMMARY";

private static final String PRINT_FINAL_MARKER =
        "FINAL_CALCULATION_SECTION";

private static final int ATTENDANCE_ROW_INDEX = 4;

private static final int FIRST_ATTENDANCE_COLUMN_INDEX = 1;

private static final int DAYS_PER_PRINT_BLOCK = 8;

private static final int MINIMUM_MONTH_DAYS = 28;

private static final int MAXIMUM_MONTH_DAYS = 31;

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

    try {
        Path excelFilePath =
                getSafeFilePath(uploadedFileName);

        if (!Files.exists(excelFilePath)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Uploaded Excel file was not found.");
        }

        try (FileInputStream inputStream =
                     new FileInputStream(excelFilePath.toFile());

             XSSFWorkbook workbook =
                     new XSSFWorkbook(inputStream)) {

            XSSFSheet sourceSheet =
                    workbook.getSheet(SOURCE_SHEET_NAME);

            if (sourceSheet == null) {
                return ResponseEntity
                        .badRequest()
                        .body(
                                SOURCE_SHEET_NAME +
                                " was not found in the Excel file."
                        );
            }

            Row firstRow =
                    sourceSheet.getRow(0);

            if (firstRow == null) {
                return ResponseEntity
                        .badRequest()
                        .body("The first row is empty.");
            }

            Row attendanceRow =
                    sourceSheet.getRow(ATTENDANCE_ROW_INDEX);

            if (attendanceRow == null) {
                return ResponseEntity
                        .badRequest()
                        .body(
                                "Attendance data was not found " +
                                "in Excel row 5."
                        );
            }

            ArrayList<String> firstRowValues =
                    readRowValues(firstRow);

            perHourSalary =
                    readEmpNameAndCalculateSalary(
                            firstRowValues
                    );

            int lastAttendanceColumn =
                    detectLastAttendanceColumn(
                            attendanceRow
                    );

            int totalAttendanceDays =
                    lastAttendanceColumn -
                    FIRST_ATTENDANCE_COLUMN_INDEX;

            if (totalAttendanceDays < MINIMUM_MONTH_DAYS ||
                    totalAttendanceDays > MAXIMUM_MONTH_DAYS) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "Expected attendance data for 28, 29, " +
                                "30 or 31 days, but found " +
                                totalAttendanceDays +
                                " day columns."
                        );
            }

            List<String> updatedTimes =
                    new ArrayList<>();

            int totalMinutes = 0;

            for (int columnIndex =
                         FIRST_ATTENDANCE_COLUMN_INDEX;
                 columnIndex < lastAttendanceColumn;
                 columnIndex++) {

                Cell attendanceCell =
                        attendanceRow.getCell(
                                columnIndex,
                                Row.MissingCellPolicy
                                        .CREATE_NULL_AS_BLANK
                        );

                String cellValue =
                        getCellValue(attendanceCell);

                String updatedTime =
                        checkAndGetUpdatedTime(cellValue);

                updatedTimes.add(updatedTime);

                if (!"MIS".equalsIgnoreCase(updatedTime)) {
                    totalMinutes +=
                            convertDurationToMinutes(
                                    updatedTime
                            );
                }
            }

            totalMonthlyWorkingHours =
                    totalMinutes / 60.0;

            totalSalaryPerMonth =
                    totalMonthlyWorkingHours *
                    perHourSalary;

            /*
             * Initial final values before AdvanceSalaryCalculation
             * is called.
             */
            afterMISandHolidayHrs_TotalCalculationHrs =
                    0.0;

            afterMIS_FinalWorkHrs =
                    totalMonthlyWorkingHours;

            beforeAdvanceCalculation_TotalSalary =
                    totalSalaryPerMonth;

            afterAllCalculationCompleted_TotalSalary =
                    totalSalaryPerMonth;

            /*
             * Remove an older generated working-hour row to
             * prevent duplicate output.
             */
            removeRowByMarker(
                    sourceSheet,
                    WORKING_SUMMARY_MARKER,
                    1
            );

            writeWorkingHoursSummaryToSourceSheet(
                    workbook,
                    sourceSheet,
                    updatedTimes,
                    lastAttendanceColumn
            );

            /*
             * Create/recreate Working_Hours_Print.
             */
            createPrintFriendlyWorkingHoursSheet(
                    workbook,
                    sourceSheet,
                    attendanceRow,
                    updatedTimes,
                    totalMinutes
            );

            workingHourCalculationCompleted = true;

            try (FileOutputStream outputStream =
                         new FileOutputStream(
                                 excelFilePath.toFile()
                         )) {

                workbook.write(outputStream);
            }
        }

        return ResponseEntity.ok(
                "Working hours updated successfully in " +
                SOURCE_SHEET_NAME +
                " and " +
                PRINT_SHEET_NAME +
                ". Employee: " +
                formatEmployeeName(employeeName) +
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
                .body(
                        "Validation error: " +
                        e.getMessage()
                );

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


    private static int detectLastAttendanceColumn(
        Row attendanceRow) {

    if (attendanceRow == null) {
        return FIRST_ATTENDANCE_COLUMN_INDEX;
    }

    int lastCellNumber =
            attendanceRow.getLastCellNum();

    if (lastCellNumber < 0) {
        return FIRST_ATTENDANCE_COLUMN_INDEX;
    }

    /*
     * Do not permit more than 31 attendance columns.
     */
    int maximumExclusiveColumn =
            Math.min(
                    lastCellNumber,
                    FIRST_ATTENDANCE_COLUMN_INDEX +
                    MAXIMUM_MONTH_DAYS
            );

    int lastPopulatedColumn =
            FIRST_ATTENDANCE_COLUMN_INDEX;

    for (int columnIndex =
                 FIRST_ATTENDANCE_COLUMN_INDEX;
         columnIndex < maximumExclusiveColumn;
         columnIndex++) {

        Cell cell =
                attendanceRow.getCell(
                        columnIndex,
                        Row.MissingCellPolicy
                                .RETURN_BLANK_AS_NULL
                );

        String value =
                getCellValue(cell);

        if (!value.isEmpty()) {
            lastPopulatedColumn =
                    columnIndex + 1;
        }
    }

    return lastPopulatedColumn;
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

    private static void writeWorkingHoursSummaryToSourceSheet(
        XSSFWorkbook workbook,
        XSSFSheet sourceSheet,
        List<String> updatedTimes,
        int lastAttendanceColumn) {

    XSSFCellStyle normalStyle =
            createNormalCellStyle(workbook);

    XSSFCellStyle misStyle =
            createMisCellStyle(workbook);

    int summaryRowIndex =
            sourceSheet.getLastRowNum() + 1;

    Row summaryRow =
            sourceSheet.createRow(summaryRowIndex);

    Cell markerCell =
            summaryRow.createCell(0);

    markerCell.setCellValue(
            WORKING_SUMMARY_MARKER
    );

    markerCell.setCellStyle(normalStyle);

    for (int index = 0;
         index < updatedTimes.size();
         index++) {

        int columnIndex =
                FIRST_ATTENDANCE_COLUMN_INDEX +
                index;

        String updatedTime =
                updatedTimes.get(index);

        Cell outputCell =
                summaryRow.createCell(columnIndex);

        if ("MIS".equalsIgnoreCase(updatedTime)) {
            outputCell.setCellValue("0.00");
            outputCell.setCellStyle(misStyle);
        } else {
            outputCell.setCellValue(updatedTime);
            outputCell.setCellStyle(normalStyle);
        }
    }

    Cell totalHoursCell =
            summaryRow.createCell(
                    lastAttendanceColumn
            );

    totalHoursCell.setCellValue(
            totalMonthlyWorkingHours
    );

    totalHoursCell.setCellStyle(normalStyle);

    Cell salaryCell =
            summaryRow.createCell(
                    lastAttendanceColumn + 1
            );

    salaryCell.setCellValue(
            totalSalaryPerMonth
    );

    salaryCell.setCellStyle(normalStyle);
}

    private static XSSFCellStyle createPrintTitleStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

    style.setFillForegroundColor(
            IndexedColors.DARK_BLUE.getIndex()
    );

    style.setFillPattern(
            FillPatternType.SOLID_FOREGROUND
    );

    XSSFFont font =
            workbook.createFont();

    font.setBold(true);
    font.setFontName("Times New Roman");
    font.setFontHeightInPoints((short) 16);
    font.setColor(IndexedColors.WHITE.getIndex());

    style.setFont(font);

    return style;
}

private static XSSFCellStyle createPrintInfoLabelStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

    style.setAlignment(HorizontalAlignment.LEFT);

    style.setFillForegroundColor(
            IndexedColors.GREY_25_PERCENT.getIndex()
    );

    style.setFillPattern(
            FillPatternType.SOLID_FOREGROUND
    );

    XSSFFont font =
            workbook.createFont();

    font.setBold(true);
    font.setFontName("Times New Roman");

    style.setFont(font);

    return style;
}

private static XSSFCellStyle createPrintInfoValueStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

    style.setAlignment(HorizontalAlignment.LEFT);
    style.setWrapText(true);

    XSSFFont font =
            workbook.createFont();

    font.setFontName("Times New Roman");
    style.setFont(font);

    return style;
}

private static XSSFCellStyle createPrintBlockTitleStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

    style.setFillForegroundColor(
            IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()
    );

    style.setFillPattern(
            FillPatternType.SOLID_FOREGROUND
    );

    XSSFFont font =
            workbook.createFont();

    font.setBold(true);
    font.setFontName("Times New Roman");
    font.setFontHeightInPoints((short) 12);

    style.setFont(font);

    return style;
}

private static XSSFCellStyle createPrintDayHeaderStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

    style.setWrapText(true);

    style.setFillForegroundColor(
            IndexedColors.LIGHT_BLUE.getIndex()
    );

    style.setFillPattern(
            FillPatternType.SOLID_FOREGROUND
    );

    XSSFFont font =
            workbook.createFont();

    font.setBold(true);
    font.setFontName("Times New Roman");

    style.setFont(font);

    return style;
}

private static XSSFCellStyle createPrintWorkingHourStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

    style.setWrapText(true);

    XSSFFont font =
            workbook.createFont();

    font.setFontName("Times New Roman");
    style.setFont(font);

    return style;
}

private static XSSFCellStyle createPrintMisStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

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
    font.setColor(IndexedColors.WHITE.getIndex());

    style.setFont(font);

    return style;
}

private static XSSFCellStyle createPrintTotalLabelStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

    style.setAlignment(HorizontalAlignment.LEFT);

    style.setFillForegroundColor(
            IndexedColors.LIGHT_GREEN.getIndex()
    );

    style.setFillPattern(
            FillPatternType.SOLID_FOREGROUND
    );

    XSSFFont font =
            workbook.createFont();

    font.setBold(true);
    font.setFontName("Times New Roman");

    style.setFont(font);

    return style;
}

private static XSSFCellStyle createPrintTotalValueStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            createBorderedCenteredStyle(workbook);

    DataFormat dataFormat =
            workbook.createDataFormat();

    style.setDataFormat(
            dataFormat.getFormat("0.00")
    );

    XSSFFont font =
            workbook.createFont();

    font.setBold(true);
    font.setFontName("Times New Roman");

    style.setFont(font);

    return style;
}

private static XSSFCellStyle createBorderedCenteredStyle(
        XSSFWorkbook workbook) {

    XSSFCellStyle style =
            workbook.createCellStyle();

    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);

    style.setBorderTop(BorderStyle.THIN);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);

    return style;
}
    private static String formatDurationFromMinutes(
        int totalMinutes) {

    int safeMinutes =
            Math.max(0, totalMinutes);

    int hours =
            safeMinutes / 60;

    int minutes =
            safeMinutes % 60;

    return String.format(
            Locale.ROOT,
            "%d:%02d",
            hours,
            minutes
    );
}

private static String formatEmployeeName(
        String name) {

    if (name == null ||
            name.trim().isEmpty()) {

        return "";
    }

    String[] parts =
            name.trim().split("\\s+");

    StringBuilder result =
            new StringBuilder();

    for (String part : parts) {

        if (part.isEmpty()) {
            continue;
        }

        if (result.length() > 0) {
            result.append(" ");
        }

        result.append(
                Character.toUpperCase(
                        part.charAt(0)
                )
        );

        if (part.length() > 1) {
            result.append(
                    part.substring(1)
                            .toLowerCase(Locale.ROOT)
            );
        }
    }

    return result.toString();
}

    private static void createPrintFriendlyWorkingHoursSheet(
        XSSFWorkbook workbook,
        XSSFSheet sourceSheet,
        Row attendanceRow,
        List<String> updatedTimes,
        int totalWorkedMinutes) {

    int existingSheetIndex =
            workbook.getSheetIndex(
                    PRINT_SHEET_NAME
            );

    if (existingSheetIndex >= 0) {
        workbook.removeSheetAt(
                existingSheetIndex
        );
    }

    XSSFSheet printSheet =
            workbook.createSheet(
                    PRINT_SHEET_NAME
            );

    configurePrintSheet(
            workbook,
            printSheet
    );

    XSSFCellStyle titleStyle =
            createPrintTitleStyle(workbook);

    XSSFCellStyle labelStyle =
            createPrintInfoLabelStyle(workbook);

    XSSFCellStyle valueStyle =
            createPrintInfoValueStyle(workbook);

    XSSFCellStyle blockTitleStyle =
            createPrintBlockTitleStyle(workbook);

    XSSFCellStyle headerStyle =
            createPrintDayHeaderStyle(workbook);

    XSSFCellStyle workingHourStyle =
            createPrintWorkingHourStyle(workbook);

    XSSFCellStyle misStyle =
            createPrintMisStyle(workbook);

    XSSFCellStyle totalLabelStyle =
            createPrintTotalLabelStyle(workbook);

    XSSFCellStyle totalValueStyle =
            createPrintTotalValueStyle(workbook);

    int currentRowIndex = 0;

    /*
     * Main title.
     */
    Row titleRow =
            printSheet.createRow(currentRowIndex++);

    titleRow.setHeightInPoints(28);

    Cell titleCell =
            titleRow.createCell(0);

    titleCell.setCellValue(
            "Employee Working Hours Summary"
    );

    titleCell.setCellStyle(titleStyle);

    printSheet.addMergedRegion(
            new CellRangeAddress(
                    titleRow.getRowNum(),
                    titleRow.getRowNum(),
                    0,
                    DAYS_PER_PRINT_BLOCK
            )
    );

    currentRowIndex++;

    currentRowIndex =
            createPrintInformationRow(
                    printSheet,
                    currentRowIndex,
                    "Employee Name",
                    formatEmployeeName(employeeName),
                    labelStyle,
                    valueStyle
            );

    currentRowIndex =
            createPrintInformationRow(
                    printSheet,
                    currentRowIndex,
                    "Per Hour Salary",
                    formatMoney(perHourSalary),
                    labelStyle,
                    valueStyle
            );

    currentRowIndex++;

    int totalAttendanceDays =
            updatedTimes.size();

    int totalBlocks =
            (totalAttendanceDays +
             DAYS_PER_PRINT_BLOCK - 1) /
            DAYS_PER_PRINT_BLOCK;

    /*
     * Dynamic block handling:
     *
     * 28 = 8 + 8 + 8 + 4
     * 29 = 8 + 8 + 8 + 5
     * 30 = 8 + 8 + 8 + 6
     * 31 = 8 + 8 + 8 + 7
     */
    for (int blockIndex = 0;
         blockIndex < totalBlocks;
         blockIndex++) {

        int startDayIndex =
                blockIndex *
                DAYS_PER_PRINT_BLOCK;

        int endDayIndex =
                Math.min(
                        startDayIndex +
                        DAYS_PER_PRINT_BLOCK,
                        totalAttendanceDays
                );

        Row blockTitleRow =
                printSheet.createRow(
                        currentRowIndex++
                );

        Cell blockTitleCell =
                blockTitleRow.createCell(0);

        blockTitleCell.setCellValue(
                "Attendance Days " +
                (startDayIndex + 1) +
                " to " +
                endDayIndex
        );

        blockTitleCell.setCellStyle(
                blockTitleStyle
        );

        printSheet.addMergedRegion(
                new CellRangeAddress(
                        blockTitleRow.getRowNum(),
                        blockTitleRow.getRowNum(),
                        0,
                        DAYS_PER_PRINT_BLOCK
                )
        );

        Row dayHeaderRow =
                printSheet.createRow(
                        currentRowIndex++
                );

        Row attendanceDetailRow =
                printSheet.createRow(
                        currentRowIndex++
                );

        Row calculatedHoursRow =
                printSheet.createRow(
                        currentRowIndex++
                );

        Cell dayLabelCell =
                dayHeaderRow.createCell(0);

        dayLabelCell.setCellValue("Day / Date");
        dayLabelCell.setCellStyle(headerStyle);

        Cell attendanceLabelCell =
                attendanceDetailRow.createCell(0);

        attendanceLabelCell.setCellValue(
                "Attendance Detail"
        );

        attendanceLabelCell.setCellStyle(
                headerStyle
        );

        Cell calculatedLabelCell =
                calculatedHoursRow.createCell(0);

        calculatedLabelCell.setCellValue(
                "Calculated Hours"
        );

        calculatedLabelCell.setCellStyle(
                headerStyle
        );

        int printColumnIndex = 1;

        for (int dayIndex = startDayIndex;
             dayIndex < endDayIndex;
             dayIndex++) {

            int sourceColumnIndex =
                    FIRST_ATTENDANCE_COLUMN_INDEX +
                    dayIndex;

            String dayHeader =
                    findAttendanceColumnHeader(
                            sourceSheet,
                            attendanceRow.getRowNum(),
                            sourceColumnIndex,
                            dayIndex + 1
                    );

            String attendanceDetails =
                    getCellValue(
                            attendanceRow.getCell(
                                    sourceColumnIndex,
                                    Row.MissingCellPolicy
                                            .CREATE_NULL_AS_BLANK
                            )
                    );

            String calculatedTime =
                    updatedTimes.get(dayIndex);

            Cell dayCell =
                    dayHeaderRow.createCell(
                            printColumnIndex
                    );

            dayCell.setCellValue(dayHeader);
            dayCell.setCellStyle(headerStyle);

            Cell attendanceCell =
                    attendanceDetailRow.createCell(
                            printColumnIndex
                    );

            attendanceCell.setCellValue(
                    attendanceDetails
            );

            attendanceCell.setCellStyle(
                    workingHourStyle
            );

            Cell calculatedCell =
                    calculatedHoursRow.createCell(
                            printColumnIndex
                    );

            if ("MIS".equalsIgnoreCase(
                    calculatedTime)) {

                calculatedCell.setCellValue("0.00");
                calculatedCell.setCellStyle(misStyle);

            } else {
                calculatedCell.setCellValue(
                        calculatedTime
                );

                calculatedCell.setCellStyle(
                        workingHourStyle
                );
            }

            printColumnIndex++;
        }

        /*
         * Add blank bordered cells to complete the
         * final group of eight.
         */
        while (printColumnIndex <=
                DAYS_PER_PRINT_BLOCK) {

            Cell emptyHeaderCell =
                    dayHeaderRow.createCell(
                            printColumnIndex
                    );

            emptyHeaderCell.setCellStyle(
                    headerStyle
            );

            Cell emptyAttendanceCell =
                    attendanceDetailRow.createCell(
                            printColumnIndex
                    );

            emptyAttendanceCell.setCellStyle(
                    workingHourStyle
            );

            Cell emptyCalculatedCell =
                    calculatedHoursRow.createCell(
                            printColumnIndex
                    );

            emptyCalculatedCell.setCellStyle(
                    workingHourStyle
            );

            printColumnIndex++;
        }

        currentRowIndex++;
    }

    /*
     * Normal working-hour calculation.
     */
    Row workingCalculationTitleRow =
            printSheet.createRow(
                    currentRowIndex++
            );

    Cell workingCalculationTitleCell =
            workingCalculationTitleRow.createCell(0);

    workingCalculationTitleCell.setCellValue(
            "WORKING HOURS CALCULATION"
    );

    workingCalculationTitleCell.setCellStyle(
            blockTitleStyle
    );

    printSheet.addMergedRegion(
            new CellRangeAddress(
                    workingCalculationTitleRow.getRowNum(),
                    workingCalculationTitleRow.getRowNum(),
                    0,
                    DAYS_PER_PRINT_BLOCK
            )
    );

    currentRowIndex =
            createPrintCalculationRow(
                    printSheet,
                    currentRowIndex,
                    "Total Working Duration",
                    formatDurationFromMinutes(
                            totalWorkedMinutes
                    ),
                    totalLabelStyle,
                    valueStyle
            );

    currentRowIndex =
            createPrintCalculationRow(
                    printSheet,
                    currentRowIndex,
                    "Total Decimal Working Hours",
                    totalMonthlyWorkingHours,
                    totalLabelStyle,
                    totalValueStyle
            );

    currentRowIndex =
            createPrintCalculationRow(
                    printSheet,
                    currentRowIndex,
                    "Per Hour Salary",
                    perHourSalary,
                    totalLabelStyle,
                    totalValueStyle
            );

    currentRowIndex =
            createPrintCalculationRow(
                    printSheet,
                    currentRowIndex,
                    "Salary Before Adjustments",
                    totalSalaryPerMonth,
                    totalLabelStyle,
                    totalValueStyle
            );

    currentRowIndex++;

    /*
     * Final calculation section.
     * AdvanceSalaryCalculation updates this section.
     */
    Row finalMarkerRow =
            printSheet.createRow(
                    currentRowIndex++
            );

    Cell finalMarkerCell =
            finalMarkerRow.createCell(0);

    finalMarkerCell.setCellValue(
            PRINT_FINAL_MARKER
    );

    finalMarkerCell.setCellStyle(
            blockTitleStyle
    );

    printSheet.addMergedRegion(
            new CellRangeAddress(
                    finalMarkerRow.getRowNum(),
                    finalMarkerRow.getRowNum(),
                    0,
                    DAYS_PER_PRINT_BLOCK
            )
    );

    createOrUpdatePrintFinalCalculationRows(
            printSheet,
            currentRowIndex,
            0.0,
            0.0,
            0.0,
            totalLabelStyle,
            totalValueStyle
    );

    printSheet.setColumnWidth(
            0,
            25 * 256
    );

    for (int columnIndex = 1;
         columnIndex <= DAYS_PER_PRINT_BLOCK;
         columnIndex++) {

        printSheet.setColumnWidth(
                columnIndex,
                16 * 256
        );
    }

    printSheet.createFreezePane(0, 5);

    int printSheetIndex =
            workbook.getSheetIndex(printSheet);

    workbook.setPrintArea(
            printSheetIndex,
            0,
            DAYS_PER_PRINT_BLOCK,
            0,
            printSheet.getLastRowNum()
    );
}

    private static void configurePrintSheet(
        XSSFWorkbook workbook,
        XSSFSheet printSheet) {

    PrintSetup printSetup =
            printSheet.getPrintSetup();

    printSetup.setPaperSize(
            PrintSetup.A4_PAPERSIZE
    );

    printSetup.setLandscape(true);
    printSetup.setFitWidth((short) 1);
    printSetup.setFitHeight((short) 0);

    printSheet.setFitToPage(true);
    printSheet.setAutobreaks(true);
    printSheet.setHorizontallyCenter(true);

    printSheet.setMargin(
            Sheet.LeftMargin,
            0.25
    );

    printSheet.setMargin(
            Sheet.RightMargin,
            0.25
    );

    printSheet.setMargin(
            Sheet.TopMargin,
            0.50
    );

    printSheet.setMargin(
            Sheet.BottomMargin,
            0.50
    );
}

private static int createPrintInformationRow(
        XSSFSheet printSheet,
        int rowIndex,
        String label,
        String value,
        XSSFCellStyle labelStyle,
        XSSFCellStyle valueStyle) {

    Row row =
            printSheet.createRow(rowIndex);

    Cell labelCell =
            row.createCell(0);

    labelCell.setCellValue(label);
    labelCell.setCellStyle(labelStyle);

    Cell valueCell =
            row.createCell(1);

    valueCell.setCellValue(value);
    valueCell.setCellStyle(valueStyle);

    printSheet.addMergedRegion(
            new CellRangeAddress(
                    rowIndex,
                    rowIndex,
                    1,
                    DAYS_PER_PRINT_BLOCK
            )
    );

    return rowIndex + 1;
}

private static int createPrintCalculationRow(
        XSSFSheet printSheet,
        int rowIndex,
        String label,
        Object value,
        XSSFCellStyle labelStyle,
        XSSFCellStyle valueStyle) {

    Row row =
            printSheet.getRow(rowIndex);

    if (row == null) {
        row = printSheet.createRow(rowIndex);
    }

    Cell labelCell =
            row.getCell(
                    0,
                    Row.MissingCellPolicy
                            .CREATE_NULL_AS_BLANK
            );

    labelCell.setCellValue(label);
    labelCell.setCellStyle(labelStyle);

    Cell valueCell =
            row.getCell(
                    1,
                    Row.MissingCellPolicy
                            .CREATE_NULL_AS_BLANK
            );

    setCellValue(valueCell, value);
    valueCell.setCellStyle(valueStyle);

    return rowIndex + 1;
}

private static String findAttendanceColumnHeader(
        XSSFSheet sourceSheet,
        int attendanceRowIndex,
        int columnIndex,
        int fallbackDayNumber) {

    DataFormatter formatter =
            new DataFormatter();

    for (int rowIndex =
                 attendanceRowIndex - 1;
         rowIndex >= 0;
         rowIndex--) {

        Row row =
                sourceSheet.getRow(rowIndex);

        if (row == null) {
            continue;
        }

        Cell cell =
                row.getCell(
                        columnIndex,
                        Row.MissingCellPolicy
                                .RETURN_BLANK_AS_NULL
                );

        if (cell == null) {
            continue;
        }

        String header =
                formatter
                        .formatCellValue(cell)
                        .trim();

        if (!header.isEmpty()) {
            return header;
        }
    }

    return "Day " + fallbackDayNumber;
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
                .body(
                        "Advance calculation request is required."
                );
    }

    double holidayHours =
            advanceCalculation.getHolidayHrs();

    double misHours =
            advanceCalculation.getMisHrs();

    double advanceSalary =
            advanceCalculation.getAdvance();

    if (!Double.isFinite(holidayHours) ||
            !Double.isFinite(misHours) ||
            !Double.isFinite(advanceSalary)) {

        return ResponseEntity
                .badRequest()
                .body(
                        "Holiday hours, MIS hours and advance " +
                        "salary must be valid numbers."
                );
    }

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
                "Final salary updated successfully in " +
                SOURCE_SHEET_NAME +
                " and " +
                PRINT_SHEET_NAME +
                ". Employee: " +
                formatEmployeeName(employeeName) +
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
                .body(
                        "Validation error: " +
                        e.getMessage()
                );

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

    Path excelFilePath =
            getSafeFilePath(uploadedFileName);

    if (!Files.exists(excelFilePath)) {
        throw new FileNotFoundException(
                "Uploaded Excel file was not found."
        );
    }

    try (FileInputStream inputStream =
                 new FileInputStream(
                         excelFilePath.toFile()
                 );

         XSSFWorkbook workbook =
                 new XSSFWorkbook(inputStream)) {

        XSSFSheet sourceSheet =
                workbook.getSheet(SOURCE_SHEET_NAME);

        XSSFSheet printSheet =
                workbook.getSheet(PRINT_SHEET_NAME);

        if (sourceSheet == null) {
            throw new IllegalArgumentException(
                    SOURCE_SHEET_NAME +
                    " was not found."
            );
        }

        if (printSheet == null) {
            throw new IllegalArgumentException(
                    PRINT_SHEET_NAME +
                    " was not found. Run WorkingHourCount first."
            );
        }

        writeOrReplaceFinalSummaryInSourceSheet(
                workbook,
                sourceSheet,
                holidayHours,
                misHours,
                advanceSalary
        );

        updateFinalCalculationInPrintSheet(
                workbook,
                printSheet,
                holidayHours,
                misHours,
                advanceSalary
        );

        try (FileOutputStream outputStream =
                     new FileOutputStream(
                             excelFilePath.toFile()
                     )) {

            workbook.write(outputStream);
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

    private static void writeOrReplaceFinalSummaryInSourceSheet(
        XSSFWorkbook workbook,
        XSSFSheet sourceSheet,
        double holidayHours,
        double misHours,
        double advanceSalary) {

    removeRowByMarker(
            sourceSheet,
            FINAL_SUMMARY_MARKER,
            3
    );

    XSSFCellStyle style =
            createFinalSummaryStyle(workbook);

    List<String> columnNames =
            getColumnNameList();

    List<Object> columnValues =
            getColumnValueList(
                    holidayHours,
                    misHours,
                    advanceSalary
            );

    int markerRowIndex =
            sourceSheet.getLastRowNum() + 2;

    int headerRowIndex =
            markerRowIndex + 1;

    int valueRowIndex =
            markerRowIndex + 2;

    Row markerRow =
            sourceSheet.createRow(markerRowIndex);

    Cell markerCell =
            markerRow.createCell(0);

    markerCell.setCellValue(
            FINAL_SUMMARY_MARKER
    );

    markerCell.setCellStyle(style);

    Row headerRow =
            sourceSheet.createRow(headerRowIndex);

    Row valueRow =
            sourceSheet.createRow(valueRowIndex);

    for (int columnIndex = 0;
         columnIndex < columnNames.size();
         columnIndex++) {

        Cell headerCell =
                headerRow.createCell(columnIndex);

        headerCell.setCellValue(
                columnNames.get(columnIndex)
        );

        headerCell.setCellStyle(style);

        Cell valueCell =
                valueRow.createCell(columnIndex);

        setCellValue(
                valueCell,
                columnValues.get(columnIndex)
        );

        valueCell.setCellStyle(style);

        sourceSheet.autoSizeColumn(columnIndex);
    }
}

private static void updateFinalCalculationInPrintSheet(
        XSSFWorkbook workbook,
        XSSFSheet printSheet,
        double holidayHours,
        double misHours,
        double advanceSalary) {

    int markerRowIndex =
            findRowByFirstCellValue(
                    printSheet,
                    PRINT_FINAL_MARKER
            );

    if (markerRowIndex < 0) {
        throw new IllegalArgumentException(
                "Final calculation section was not found in " +
                PRINT_SHEET_NAME
        );
    }

    XSSFCellStyle labelStyle =
            createPrintTotalLabelStyle(workbook);

    XSSFCellStyle valueStyle =
            createPrintTotalValueStyle(workbook);

    createOrUpdatePrintFinalCalculationRows(
            printSheet,
            markerRowIndex + 1,
            holidayHours,
            misHours,
            advanceSalary,
            labelStyle,
            valueStyle
    );
}

private static void createOrUpdatePrintFinalCalculationRows(
        XSSFSheet printSheet,
        int startingRowIndex,
        double holidayHours,
        double misHours,
        double advanceSalary,
        XSSFCellStyle labelStyle,
        XSSFCellStyle valueStyle) {

    int rowIndex = startingRowIndex;

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Employee Name",
            formatEmployeeName(employeeName),
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Per Hour Salary",
            perHourSalary,
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Before Total Working Hours",
            totalMonthlyWorkingHours,
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Before Total Salary",
            totalSalaryPerMonth,
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Holiday Hours",
            holidayHours,
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "MIS Hours",
            misHours,
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "MIS + Holiday Hours",
            afterMISandHolidayHrs_TotalCalculationHrs,
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Final Working Hours",
            afterMIS_FinalWorkHrs,
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Salary Before Advance",
            beforeAdvanceCalculation_TotalSalary,
            labelStyle,
            valueStyle
    );

    rowIndex = createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Advance Salary",
            advanceSalary,
            labelStyle,
            valueStyle
    );

    createPrintCalculationRow(
            printSheet,
            rowIndex,
            "Final Salary",
            afterAllCalculationCompleted_TotalSalary,
            labelStyle,
            valueStyle
    );
}

    private static int findRowByFirstCellValue(
        XSSFSheet sheet,
        String expectedValue) {

    for (int rowIndex = 0;
         rowIndex <= sheet.getLastRowNum();
         rowIndex++) {

        Row row =
                sheet.getRow(rowIndex);

        if (row == null) {
            continue;
        }

        Cell firstCell =
                row.getCell(
                        0,
                        Row.MissingCellPolicy
                                .RETURN_BLANK_AS_NULL
                );

        if (firstCell == null) {
            continue;
        }

        if (expectedValue.equalsIgnoreCase(
                getCellValue(firstCell))) {

            return rowIndex;
        }
    }

    return -1;
}

private static void removeRowByMarker(
        XSSFSheet sheet,
        String marker,
        int numberOfRows) {

    int markerRowIndex =
            findRowByFirstCellValue(
                    sheet,
                    marker
            );

    if (markerRowIndex < 0) {
        return;
    }

    int lastRowToRemove =
            Math.min(
                    markerRowIndex + numberOfRows - 1,
                    sheet.getLastRowNum()
            );

    for (int rowIndex =
                 lastRowToRemove;
         rowIndex >= markerRowIndex;
         rowIndex--) {

        Row row =
                sheet.getRow(rowIndex);

        if (row != null) {
            sheet.removeRow(row);
        }
    }
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
