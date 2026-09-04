render project deployee this project and email id is my personal email okay



# Expert Industries Attendance and Salary Management System

## 1. Project Overview

The **Expert Industries Attendance and Salary Management System** is a Spring Boot application that processes employee attendance data from an Excel workbook.

The application reads daily attendance information, calculates working hours, deducts break time, identifies the employee, retrieves the employee’s hourly salary, and calculates the monthly salary.

It also supports final salary adjustments such as:

- Holiday hours
- MIS hours
- Advance salary deductions

The processed results are updated in:

1. The original worksheet named `Sheet1`
2. A separate print-friendly worksheet named `Working_Hours_Print`

The print-friendly worksheet divides monthly attendance into blocks of eight days so that data for 28, 29, 30, or 31-day months can be displayed and printed clearly on A4 paper.

---

## 2. Main Features

The application provides the following functionalities:

- Upload an Excel `.xlsx` attendance file
- Validate the uploaded file
- Store the uploaded Excel file on the server
- List uploaded files
- Download processed Excel files
- Delete uploaded files
- Read the employee name from Excel
- Find the employee’s salary per hour
- Read daily attendance details
- Calculate the daily payable working hours
- Deduct 30 minutes for attendance status `P`
- Prevent negative time calculation
- Handle `MIS` attendance status
- Calculate monthly total working minutes
- Convert total working minutes into decimal working hours
- Calculate salary before adjustments
- Add holiday hours
- Add MIS hours
- Deduct advance salary
- Calculate final payable salary
- Update the original Excel sheet
- Create a print-friendly Excel sheet
- Support 28, 29, 30, and 31-day months
- Divide attendance data into groups of eight days
- Prevent duplicate generated summary rows
- Configure the print sheet for A4 landscape printing

---

## 3. Technology Stack

The project uses the following technologies:

- Java
- Spring Boot
- Spring Web
- Apache POI
- Swagger/OpenAPI
- Microsoft Excel `.xlsx`
- Maven or Gradle
- REST APIs

---

## 4. Main Java Classes

### 4.1 DemoController

The main controller class is:

```text
expert.industries.render.Controller.DemoController
```

This class is responsible for:

- Receiving API requests
- Uploading files
- Downloading files
- Deleting files
- Listing files
- Reading Excel data
- Calculating attendance
- Calculating salary
- Updating Excel sheets
- Creating the print-friendly worksheet

### 4.2 AdvanceCalculation

The request entity for the final salary calculation is:

```text
expert.industries.render.Entity.AdvanceCalculation
```

The entity should contain:

```java
private double holidayHrs;
private double misHrs;
private double advance;
```

It should provide these getter methods:

```java
getHolidayHrs();
getMisHrs();
getAdvance();
```

Example entity:

```java
package expert.industries.render.Entity;

public class AdvanceCalculation {

    private double holidayHrs;
    private double misHrs;
    private double advance;

    public double getHolidayHrs() {
        return holidayHrs;
    }

    public void setHolidayHrs(double holidayHrs) {
        this.holidayHrs = holidayHrs;
    }

    public double getMisHrs() {
        return misHrs;
    }

    public void setMisHrs(double misHrs) {
        this.misHrs = misHrs;
    }

    public double getAdvance() {
        return advance;
    }

    public void setAdvance(double advance) {
        this.advance = advance;
    }
}
```

---

## 5. Project Workflow

The APIs should be called in the following sequence:

```text
1. Upload Excel file
         |
         v
2. Calculate working hours
         |
         v
3. Apply holiday, MIS, and advance adjustments
         |
         v
4. Download the updated Excel file
```

The actual API order is:

```text
POST /api/upload
GET  /api/MotiExcelSheet/WorkingHourCount
POST /AdvanceSalaryCalculation
GET  /download/{filename}
```

The `AdvanceSalaryCalculation` API must not be called before the `WorkingHourCount` API.

---

## 6. Required Excel Structure

### 6.1 Worksheet name

The uploaded workbook must contain a worksheet named:

```text
Sheet1
```

The application looks for this exact worksheet name.

If the worksheet is missing, the API returns:

```text
Sheet1 was not found in the Excel file.
```

### 6.2 Employee name location

The employee name must be available in the first Excel row.

Expected format:

```text
Emp Name : Gajanan Raut
```

The application searches the first row for a cell containing:

```text
Emp Name
```

It then splits the value using the colon.

Example:

```java
employeeNameFormat.split(":", 2);
```

Input:

```text
Emp Name : Gajanan Raut
```

Extracted value:

```text
Gajanan Raut
```

Normalized value used internally:

```text
gajanan raut
```

The employee name is converted to lowercase so that salary lookup is consistent.

### 6.3 Attendance row location

Attendance data is read from Excel row 5.

Apache POI uses zero-based indexing, so row 5 is represented by:

```java
private static final int ATTENDANCE_ROW_INDEX = 4;
```

Attendance begins from column index 1:

```java
private static final int FIRST_ATTENDANCE_COLUMN_INDEX = 1;
```

Column index 0 is treated as a non-attendance column.

---

## 7. Attendance Cell Format

Each daily attendance cell should contain multiple lines.

The final two lines must contain:

1. Total working duration in `H:mm` format
2. Attendance status

Example:

```text
08:52 AM
09:20 AM
0:28
P
```

The application reads:

```text
Total duration = 0:28
Attendance status = P
```

The current implementation does not calculate the duration directly from the punch-in and punch-out values.

It expects the calculated duration to already be available in the second-last line.

---

## 8. Attendance Status Handling

### 8.1 Present status: `P`

When the attendance status is `P`, the application deducts 30 minutes from the recorded duration.

Example:

```text
Recorded duration = 8:30
Break deduction   = 0:30
Payable duration  = 8:00
```

The calculation is performed using total minutes:

```java
int totalMinutes = (hours * 60) + minutes;
totalMinutes = Math.max(0, totalMinutes - 30);
```

### 8.2 MIS status: `MIS`

When the status is `MIS`, the daily calculated result is returned as:

```text
MIS
```

When writing the calculated attendance into Excel, `MIS` is displayed as:

```text
0.00
```

MIS compensation can later be added through the `AdvanceSalaryCalculation` API.

### 8.3 Blank attendance cell

A blank attendance cell is currently treated as:

```text
0.00
```

This means the blank day contributes zero working minutes.

### 8.4 Other statuses

For statuses other than `P` and `MIS`, the code currently keeps the provided duration without applying the 30-minute deduction.

If additional statuses are required, such as:

- `A` for Absent
- `H` for Holiday
- `WO` for Weekly Off
- `L` for Leave
- `HD` for Half Day

then their business rules should be added inside:

```java
checkAndGetUpdatedTime()
```

---

## 9. The `23.58` Time Calculation Bug

### 9.1 Previous behavior

The old implementation used `LocalTime`:

```java
LocalTime time = LocalTime.parse(totalTime, formatter);
time = time.minusMinutes(30);
```

This caused an incorrect result when the duration was less than 30 minutes.

Example:

```text
Employee duration = 0:28
Deduction         = 0:30
```

Expected mathematical result:

```text
-2 minutes
```

However, `LocalTime` represents a clock time and cannot represent negative duration.

Therefore, Java wrapped the time to the previous day:

```text
00:28 - 00:30 = 23:58
```

This resulted in the incorrect value:

```text
23.58
```

That could incorrectly appear as 23 hours and 58 minutes of payable work.

### 9.2 Corrected behavior

The corrected code converts the duration into total minutes:

```java
int totalMinutes = (hours * 60) + minutes;
```

It then applies the deduction safely:

```java
totalMinutes = Math.max(0, totalMinutes - 30);
```

For a 28-minute duration:

```text
28 - 30 = -2
```

After applying `Math.max()`:

```text
Maximum of 0 and -2 = 0
```

Final result:

```text
0.00
```

This prevents a negative duration from becoming `23.58`.

---

## 10. Daily Display Duration

Daily duration is displayed in the format:

```text
H.mm
```

Examples:

```text
8.30 = 8 hours and 30 minutes
7.05 = 7 hours and 5 minutes
0.28 = 28 minutes
0.00 = 0 hours and 0 minutes
```

This format is used for human-readable Excel output.

It is not a true decimal-hour value.

---

## 11. Display Duration Versus Decimal Payroll Hours

This distinction is very important.

### Display format

```text
8.30
```

means:

```text
8 hours and 30 minutes
```

### Decimal payroll format

Eight hours and thirty minutes equals:

```text
8.50 decimal hours
```

because:

```text
30 minutes / 60 minutes = 0.50 hours
```

### Examples

| Duration | Display value | Decimal payroll value |
|---|---:|---:|
| 8 hours 0 minutes | 8.00 | 8.00 |
| 8 hours 15 minutes | 8.15 | 8.25 |
| 8 hours 30 minutes | 8.30 | 8.50 |
| 8 hours 45 minutes | 8.45 | 8.75 |
| 40 hours 30 minutes | 40.30 | 40.50 |

The daily output can use `H.mm`, but salary must be calculated using decimal hours.

---

## 12. Correct Monthly Hours Calculation

The application adds each day’s duration in minutes.

Example:

```java
totalMinutes += hours * 60 + minutes;
```

After all attendance days are processed, total minutes are converted to decimal hours:

```java
totalMonthlyWorkingHours = totalMinutes / 60.0;
```

Example:

```text
Total minutes = 2430
```

Calculation:

```text
2430 / 60 = 40.50 hours
```

Therefore:

```text
Total decimal working hours = 40.50
```

### Incorrect approach that should not be used

The following is incorrect:

```java
Double.parseDouble(totalHours + "." + remainingMinutes);
```

For 40 hours and 30 minutes, this produces:

```text
40.30
```

However, the correct decimal payroll value is:

```text
40.50
```

---

## 13. Employee Salary Configuration

Employee hourly salary is currently configured using a Java `Map`.

Example:

```java
private static Map<String, Double> createEmployeeSalaryMap() {

    Map<String, Double> salaryMap =
            new HashMap<>();

    salaryMap.put("dadasaheb kolhe", 118.75);
    salaryMap.put("gajanan raut", 90.00);
    salaryMap.put("bhagyavendra singh", 100.00);
    salaryMap.put("salim mohameed", 106.25);
    salaryMap.put("alim", 93.75);
    salaryMap.put("mahindra", 93.75);

    return salaryMap;
}
```

### Adding a new employee

Always add the employee name in lowercase:

```java
salaryMap.put("biswajit sahoo", 125.00);
```

The employee name from Excel is also converted to lowercase:

```java
employeeName = employeeNameParts[1]
        .trim()
        .toLowerCase(Locale.ROOT);
```

The salary is retrieved using:

```java
Double salary = salaryMap.get(employeeName);
```

### Missing salary configuration

If the employee is not found in the salary map, the application stops processing and returns an error.

Example:

```text
Per-hour salary is not configured for employee: employee name
```

This is safer than silently assigning a salary of zero.

---

## 14. Working Hours API

### Endpoint

```http
GET /api/MotiExcelSheet/WorkingHourCount
```

### Purpose

This API reads attendance from the uploaded Excel workbook and calculates working hours and initial salary.

### Processing steps

The API performs the following operations:

1. Verifies that an Excel file was uploaded.
2. Locates the uploaded file.
3. Opens the workbook.
4. Locates `Sheet1`.
5. Reads the first row.
6. Extracts the employee name.
7. Finds the employee’s hourly salary.
8. Reads attendance from row 5.
9. Detects the number of attendance days.
10. Validates that the month contains 28 to 31 days.
11. Processes every attendance cell.
12. Applies the 30-minute deduction for `P`.
13. Converts `MIS` into zero calculated attendance time.
14. Adds all valid durations in minutes.
15. Converts total minutes into decimal working hours.
16. Calculates salary before final adjustments.
17. Writes daily calculated values into `Sheet1`.
18. Creates or recreates `Working_Hours_Print`.
19. Saves the updated workbook.

### Initial salary formula

```text
Initial Salary =
Total Monthly Decimal Working Hours × Per-Hour Salary
```

Example:

```text
Total monthly working hours = 160.50
Per-hour salary             = 100.00

Initial salary = 160.50 × 100.00
Initial salary = 16,050.00
```

---

## 15. Month-Length Handling

The application supports the following month lengths:

| Month length | Eight-day grouping |
|---:|---|
| 28 days | 8 + 8 + 8 + 4 |
| 29 days | 8 + 8 + 8 + 5 |
| 30 days | 8 + 8 + 8 + 6 |
| 31 days | 8 + 8 + 8 + 7 |

The number of print blocks is calculated using:

```java
int totalBlocks =
        (totalAttendanceDays +
         DAYS_PER_PRINT_BLOCK - 1)
        / DAYS_PER_PRINT_BLOCK;
```

The block size is configured as:

```java
private static final int DAYS_PER_PRINT_BLOCK = 8;
```

### 28-day month

```text
Block 1 = Day 1 to Day 8
Block 2 = Day 9 to Day 16
Block 3 = Day 17 to Day 24
Block 4 = Day 25 to Day 28
```

### 29-day month

```text
Block 1 = Day 1 to Day 8
Block 2 = Day 9 to Day 16
Block 3 = Day 17 to Day 24
Block 4 = Day 25 to Day 29
```

### 30-day month

```text
Block 1 = Day 1 to Day 8
Block 2 = Day 9 to Day 16
Block 3 = Day 17 to Day 24
Block 4 = Day 25 to Day 30
```

### 31-day month

```text
Block 1 = Day 1 to Day 8
Block 2 = Day 9 to Day 16
Block 3 = Day 17 to Day 24
Block 4 = Day 25 to Day 31
```

Unused cells in the last print block are left blank but retain borders and styles.

---

## 16. Working_Hours_Print Worksheet

The application creates a separate worksheet named:

```text
Working_Hours_Print
```

### Why a separate worksheet is used

The source worksheet is not rearranged because changing its structure could affect attendance processing when the API is called again.

The separate worksheet provides:

- A clean layout
- Better readability
- Better print support
- Protection of the original attendance layout
- Separation of input data and generated reports

### Repeated API execution

When `WorkingHourCount` is called again:

1. The existing `Working_Hours_Print` worksheet is detected.
2. The old worksheet is removed.
3. A new print worksheet is created.

This prevents duplicate or outdated printable data.

---

## 17. Working_Hours_Print Layout

The print worksheet contains:

### Employee information

```text
Employee Name
Per Hour Salary
```

### Attendance groups

Each group contains:

```text
Attendance Days 1 to 8
Day / Date
Attendance Detail
Calculated Hours
```

Example:

```text
Attendance Days 1 to 8

Day / Date         Day 1   Day 2   Day 3   Day 4   Day 5   Day 6   Day 7   Day 8
Attendance Detail  ...     ...     ...     ...     ...     ...     ...     ...
Calculated Hours   8.00    7.45    0.00    8.10    8.00    7.30    0.00    8.15
```

The next group is added below the previous group.

### Working-hours calculation

The print sheet also contains:

```text
Total Working Duration
Total Decimal Working Hours
Per Hour Salary
Salary Before Adjustments
```

### Final calculation

After the final salary API is called, the print sheet includes:

```text
Employee Name
Per Hour Salary
Before Total Working Hours
Before Total Salary
Holiday Hours
MIS Hours
MIS + Holiday Hours
Final Working Hours
Salary Before Advance
Advance Salary
Final Salary
```

---

## 18. Print Configuration

The `Working_Hours_Print` worksheet is configured for printing.

The settings include:

```text
Paper size      = A4
Orientation     = Landscape
Fit width       = One page
Fit height      = Automatic
Horizontal      = Centered
Margins         = Reduced
Columns         = One label column plus eight day columns
```

Apache POI configuration:

```java
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
```

The print area contains columns 0 through 8:

```text
Column 0 = Description
Columns 1 to 8 = Attendance days
```

---

## 19. Final Salary API

### Endpoint

```http
POST /AdvanceSalaryCalculation
```

### Request content type

```text
application/json
```

### Example request body

```json
{
  "holidayHrs": 8.0,
  "misHrs": 4.0,
  "advance": 2000.0
}
```

### Important input format

The `holidayHrs` and `misHrs` values are treated as decimal hours.

Example:

```text
8.5 means 8 hours and 30 minutes
```

It does not mean:

```text
8 hours and 5 minutes
```

### Validation

The API validates that:

- The working-hours calculation has already completed.
- The request body is available.
- Holiday hours are not negative.
- MIS hours are not negative.
- Advance salary is not negative.
- The input values are valid finite numbers.

---

## 20. Final Salary Calculation

### Step 1: Calculate additional hours

```text
Additional Hours =
Holiday Hours + MIS Hours
```

Java calculation:

```java
afterMISandHolidayHrs_TotalCalculationHrs =
        holidayHours + misHours;
```

### Step 2: Calculate final working hours

```text
Final Working Hours =
Base Monthly Working Hours + Additional Hours
```

Java calculation:

```java
afterMIS_FinalWorkHrs =
        totalMonthlyWorkingHours +
        afterMISandHolidayHrs_TotalCalculationHrs;
```

### Step 3: Calculate salary before advance

```text
Salary Before Advance =
Final Working Hours × Per-Hour Salary
```

Java calculation:

```java
beforeAdvanceCalculation_TotalSalary =
        afterMIS_FinalWorkHrs *
        perHourSalary;
```

### Step 4: Subtract advance

```text
Final Salary =
Salary Before Advance - Advance Salary
```

Java calculation:

```java
afterAllCalculationCompleted_TotalSalary =
        beforeAdvanceCalculation_TotalSalary -
        advanceSalary;
```

### Step 5: Prevent negative final salary

The current business rule prevents final salary from becoming negative:

```java
afterAllCalculationCompleted_TotalSalary =
        Math.max(
                0,
                afterAllCalculationCompleted_TotalSalary
        );
```

If the organization allows negative balances or employee recovery amounts, this rule should be changed.

---

## 21. Salary Calculation Example

Assume:

```text
Monthly attendance duration = 160 hours 30 minutes
Per-hour salary             = 100.00
Holiday hours               = 8.00
MIS hours                   = 4.00
Advance salary              = 2,000.00
```

### Convert duration to decimal hours

```text
160 hours 30 minutes = 160.50 hours
```

### Calculate salary before adjustments

```text
160.50 × 100.00 = 16,050.00
```

### Calculate additional hours

```text
Holiday hours + MIS hours

8.00 + 4.00 = 12.00 hours
```

### Calculate final hours

```text
160.50 + 12.00 = 172.50 hours
```

### Calculate salary before advance

```text
172.50 × 100.00 = 17,250.00
```

### Deduct advance

```text
17,250.00 - 2,000.00 = 15,250.00
```

Final salary:

```text
15,250.00
```

---

## 22. Final Summary in Sheet1

The final salary summary contains the following columns:

| Column name | Description |
|---|---|
| `Employee_Name` | Employee name extracted from Excel |
| `Per_Hour_Salary` | Configured hourly salary |
| `Before_TotalWorkHrs` | Working hours before holiday and MIS adjustments |
| `Before_TotalSalary` | Salary before final adjustments |
| `Holiday_Hrs` | Holiday hours received from the API |
| `MIS_Hrs` | MIS hours received from the API |
| `MIS_Plus_Holiday_Hrs` | Holiday and MIS hours combined |
| `FinalWorkHrs` | Base hours plus holiday and MIS hours |
| `BeforeAdvanceCalculation_Salary` | Salary after additional hours and before advance |
| `Advance_Salary` | Advance amount to deduct |
| `FinalSalary` | Final payable salary |

---

## 23. Updating Both Worksheets

The final calculation is updated in both:

```text
Sheet1
Working_Hours_Print
```

### Sheet1

The source worksheet receives a detailed final summary with column headers and values.

### Working_Hours_Print

The print worksheet receives the same final calculation in a vertical, print-friendly format.

This makes it possible to:

- Retain the original attendance records
- View the complete calculation
- Print a readable employee salary summary
- Verify the salary basis
- See the per-hour salary
- See all adjustments

---

## 24. Duplicate Prevention

Generated sections use marker values.

Examples:

```text
CALCULATED_WORKING_HOURS
FINAL_SALARY_SUMMARY
FINAL_CALCULATION_SECTION
```

Before creating a generated section, the application searches for the corresponding marker.

If the marker exists:

1. The previously generated rows are removed.
2. New rows are created.
3. Updated values are written.

This prevents repeated API calls from continuously adding duplicate summaries.

The `Working_Hours_Print` worksheet is recreated completely whenever the working-hours API is executed.

---

## 25. API Documentation

### 25.1 Welcome API

```http
GET /api/Welcome
```

Response:

```text
Welcome to Expert Industries
```

### 25.2 Greeting API

```http
GET /api/greet?name=Biswajit
```

Response:

```text
Hello, My Dear Biswajit!
```

### 25.3 Upload API

```http
POST /api/upload
```

Content type:

```text
multipart/form-data
```

Form field:

```text
file
```

Only `.xlsx` files are accepted.

Example response:

```text
File uploaded successfully: attendance.xlsx
```

### 25.4 List Files API

```http
GET /api/listOfFiles
```

This endpoint lists available files in the upload directory.

The file `app.jar` is excluded.

### 25.5 Delete File API

```http
DELETE /api/delete/{filename}
```

Example:

```http
DELETE /api/delete/attendance.xlsx
```

If the currently active file is deleted, calculation values are reset.

### 25.6 Working Hours API

```http
GET /api/MotiExcelSheet/WorkingHourCount
```

This endpoint:

- Reads attendance
- Calculates daily hours
- Calculates monthly hours
- Calculates the initial salary
- Updates `Sheet1`
- Creates `Working_Hours_Print`

### 25.7 Advance Salary API

```http
POST /AdvanceSalaryCalculation
```

Example request:

```json
{
  "holidayHrs": 8.0,
  "misHrs": 4.0,
  "advance": 2000.0
}
```

### 25.8 Download API

```http
GET /download/{filename}
```

Example:

```http
GET /download/attendance.xlsx
```

---

## 26. Example cURL Commands

### Upload the workbook

```bash
curl -X POST \
  -F "file=@attendance.xlsx" \
  http://localhost:8080/api/upload
```

### Run working-hours calculation

```bash
curl -X GET \
  http://localhost:8080/api/MotiExcelSheet/WorkingHourCount
```

### Run final salary calculation

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
        "holidayHrs": 8.0,
        "misHrs": 4.0,
        "advance": 2000.0
      }' \
  http://localhost:8080/AdvanceSalaryCalculation
```

### Download the processed workbook

```bash
curl -O \
  http://localhost:8080/download/attendance.xlsx
```

### List files

```bash
curl -X GET \
  http://localhost:8080/api/listOfFiles
```

### Delete a file

```bash
curl -X DELETE \
  http://localhost:8080/api/delete/attendance.xlsx
```

---

## 27. File Validation

The upload API validates:

- The file is not null.
- The file is not empty.
- The filename is available.
- The extension is `.xlsx`.
- The final file path remains inside the configured upload directory.

A safe filename is generated using:

```java
String safeFilename =
        Paths.get(originalFilename)
                .getFileName()
                .toString();
```

This removes directory information supplied with the filename.

The destination path is normalized and validated:

```java
Path destinationPath =
        uploadDirectory
                .resolve(safeFilename)
                .normalize();

if (!destinationPath.startsWith(uploadDirectory)) {
    throw new IllegalArgumentException(
            "Invalid upload file path."
    );
}
```

---

## 28. Workbook Validation

Before processing, the application checks:

- The uploaded filename is available.
- The uploaded file exists.
- The workbook can be opened.
- `Sheet1` exists.
- Excel row 1 exists.
- Attendance row 5 exists.
- Employee name is available.
- Employee salary is configured.
- Attendance columns exist.
- The month contains 28 to 31 days.

---

## 29. Duration Validation

The duration must use:

```text
H:mm
```

Valid examples:

```text
0:28
8:00
8:30
10:45
```

Invalid examples:

```text
8.75
8:75
eight hours
8:
:30
```

The application validates:

```java
if (hours < 0 ||
        minutes < 0 ||
        minutes > 59) {

    throw new IllegalArgumentException(
            "Invalid duration: " + totalTime
    );
}
```

---

## 30. Error Handling

### File was not uploaded

```text
Please upload an Excel file first.
```

### File was not found

```text
Uploaded Excel file was not found.
```

### Sheet1 does not exist

```text
Sheet1 was not found in the Excel file.
```

### First row is empty

```text
The first row is empty.
```

### Attendance row is missing

```text
Attendance data was not found in Excel row 5.
```

### Employee name is missing

```text
Employee name was not found in the first row.
Expected format: Emp Name : Employee Name
```

### Salary is missing

```text
Per-hour salary is not configured for employee: employee name
```

### Invalid attendance format

```text
Invalid attendance cell value.
Expected duration and attendance status.
```

### Invalid duration

```text
Invalid duration format.
Expected H:mm format.
```

### Incorrect number of day columns

```text
Expected attendance data for 28, 29, 30 or 31 days.
```

### Final API called too early

```text
Please execute WorkingHourCount API before AdvanceSalaryCalculation API.
```

### Print worksheet is missing

```text
Working_Hours_Print was not found.
Run WorkingHourCount first.
```

---

## 31. Testing Checklist

### File upload testing

- [ ] Upload a valid `.xlsx` file
- [ ] Upload an empty file
- [ ] Upload a `.pdf` file
- [ ] Upload a `.xls` file
- [ ] Upload a file with an unsafe filename
- [ ] Upload a replacement file with the same name

### Employee testing

- [ ] Employee name exists in row 1
- [ ] Employee name has uppercase letters
- [ ] Employee name has extra spaces
- [ ] Employee name does not contain a colon
- [ ] Employee salary exists in the map
- [ ] Employee salary is missing from the map

### Duration testing

- [ ] `0:28` with `P` returns `0.00`
- [ ] `0:30` with `P` returns `0.00`
- [ ] `0:45` with `P` returns `0.15`
- [ ] `8:30` with `P` returns `8.00`
- [ ] `8:30` with `MIS` returns `MIS`
- [ ] Blank cell returns `0.00`
- [ ] `8:75` is rejected
- [ ] Non-numeric duration is rejected

### Month testing

- [ ] 28 days create `8 + 8 + 8 + 4`
- [ ] 29 days create `8 + 8 + 8 + 5`
- [ ] 30 days create `8 + 8 + 8 + 6`
- [ ] 31 days create `8 + 8 + 8 + 7`
- [ ] Less than 28 days is rejected
- [ ] More than 31 days is not processed as attendance

### Salary testing

- [ ] Base salary is calculated correctly
- [ ] Thirty minutes becomes `0.50` decimal hours
- [ ] Holiday hours are added correctly
- [ ] MIS hours are added correctly
- [ ] Advance salary is deducted correctly
- [ ] Negative holiday hours are rejected
- [ ] Negative MIS hours are rejected
- [ ] Negative advance salary is rejected
- [ ] Final salary does not become negative

### Excel output testing

- [ ] `Sheet1` remains available
- [ ] Calculated hours are added to `Sheet1`
- [ ] `Working_Hours_Print` is created
- [ ] Employee name is shown
- [ ] Per-hour salary is shown
- [ ] Attendance data is divided into groups of eight
- [ ] Final calculations appear in both sheets
- [ ] Re-running the API does not create duplicate output
- [ ] A4 print preview is readable

---

## 32. Important Business Rules to Confirm

Before using this application for actual employee payroll, confirm these rules with the authorized payroll or HR owner:

1. Should every `P` attendance record receive a 30-minute deduction?
2. Should work below 30 minutes become zero?
3. Should the deduction be skipped if the employee worked for less than 30 minutes?
4. Should a blank attendance cell mean zero, absent, or invalid?
5. Should `MIS` records be compensated manually?
6. Are holiday hours entered as decimal hours?
7. Are MIS hours entered as decimal hours?
8. Can final salary become negative?
9. How should overtime be calculated?
10. How should weekly offs be handled?
11. How should paid leave be handled?
12. How should half-day attendance be handled?
13. How should night shifts be handled?
14. How should missing punch-in or punch-out records be handled?
15. At which stage should salary values be rounded?

These are business decisions. They should not be decided only through technical code.

---

## 33. Known Limitations

### 33.1 Static shared variables

The application currently stores calculation information in static fields:

```java
public static String uploadedFileName;
public static String employeeName;
public static double totalMonthlyWorkingHours;
public static double perHourSalary;
```

This can work for a controlled single-user process.

It is not safe for multiple employees or multiple users processing files simultaneously because one request could overwrite another request’s values.

Recommended future solution:

- Store calculation values by filename
- Use a unique processing ID
- Use HTTP session storage
- Use a database
- Move calculations into a service object
- Avoid mutable static fields

### 33.2 Hard-coded Excel locations

The application assumes:

```text
Employee name is in row 1
Attendance is in row 5
Attendance starts from column index 1
```

If the workbook format changes, the constants must be updated.

### 33.3 Hard-coded salary map

Employee salary rates are stored directly in Java code.

For production usage, salary information should be stored in:

- A secured database
- An encrypted configuration source
- A protected HR system
- A restricted salary master workbook

### 33.4 Local file storage

The application stores uploaded workbooks under:

```java
System.getProperty("user.dir")
```

On some cloud platforms, local files may be temporary and may disappear after restart or deployment.

For production usage, consider:

- Persistent disk storage
- Object storage
- Database-backed storage
- A secured document repository

### 33.5 Use of `double` for money

The application currently uses `double` for salary.

For production-level financial calculations, `BigDecimal` is safer because floating-point values may have precision differences.

Example future change:

```java
BigDecimal salaryPerHour;
BigDecimal finalSalary;
```

### 33.6 Duration is read instead of independently calculated

The code reads the duration from the second-last line of the attendance cell.

It does not independently verify:

```text
Punch-out time - Punch-in time = Reported duration
```

For stronger validation, the application can later calculate punch duration from entry and exit timestamps.

---

## 34. Recommended Project Architecture

The current implementation keeps most logic in `DemoController`.

For better maintenance, split the code into smaller classes.

Recommended structure:

```text
src/main/java/expert/industries/render/
├── Controller/
│   └── DemoController.java
├── Entity/
│   └── AdvanceCalculation.java
├── Service/
│   ├── AttendanceCalculationService.java
│   ├── SalaryCalculationService.java
│   ├── ExcelProcessingService.java
│   └── PrintSheetService.java
├── Repository/
│   └── EmployeeSalaryRepository.java
├── DTO/
│   ├── WorkingHourResult.java
│   └── SalaryCalculationResult.java
└── Exception/
    └── GlobalExceptionHandler.java
```

### DemoController

Responsibilities:

- Receive HTTP requests
- Validate basic request information
- Call service methods
- Return HTTP responses

### AttendanceCalculationService

Responsibilities:

- Parse attendance cells
- Apply break deductions
- Convert duration into minutes
- Calculate total working hours

### SalaryCalculationService

Responsibilities:

- Calculate base salary
- Add holiday and MIS hours
- Subtract advance salary
- Calculate final salary

### ExcelProcessingService

Responsibilities:

- Open the workbook
- Read source data
- Write calculated rows
- Save workbooks
- Manage generated markers

### PrintSheetService

Responsibilities:

- Create `Working_Hours_Print`
- Divide days into blocks of eight
- Apply Excel styles
- Configure A4 printing
- Update final print calculations

### EmployeeSalaryRepository

Responsibilities:

- Retrieve employee salary
- Replace the hard-coded Java map
- Connect to a database or secured salary source

---

## 35. Security Recommendations

Before deploying this system for real payroll usage:

- Add user authentication
- Add role-based authorization
- Restrict salary APIs to authorized employees
- Restrict file download access
- Restrict file deletion access
- Replace `@CrossOrigin(origins = "*")`
- Allow only trusted frontend domains
- Validate maximum upload size
- Validate workbook contents
- Add audit logs
- Record who uploaded a file
- Record who changed salary inputs
- Protect salary information
- Avoid exposing raw exception messages
- Store payroll files securely
- Use HTTPS
- Use persistent storage
- Use `BigDecimal` for salary
- Add automated tests

Example restricted CORS configuration:

```java
@CrossOrigin(origins = "https://your-trusted-frontend.example")
```

---

## 36. Main Bugs Corrected

### Bug 1: Negative time became `23.58`

Cause:

```java
LocalTime.minusMinutes(30)
```

Correction:

```java
Math.max(0, totalMinutes - 30)
```

### Bug 2: Minutes were treated like decimal fractions

Incorrect:

```text
40 hours 30 minutes = 40.30
```

Correct:

```text
40 hours 30 minutes = 40.50 decimal hours
```

Correction:

```java
totalMonthlyWorkingHours =
        totalMinutes / 60.0;
```

### Bug 3: Salary lookup could silently return zero

Correction:

- Normalize the employee name
- Use direct map lookup
- Throw a validation error when salary is not configured

### Bug 4: Employee name was not shown in final output

Correction:

Added:

```text
Employee_Name
```

to the final summary.

### Bug 5: Per-hour salary was not shown

Correction:

Added:

```text
Per_Hour_Salary
```

to the final summary.

### Bug 6: Long monthly attendance was difficult to print

Correction:

Created:

```text
Working_Hours_Print
```

and divided attendance into blocks of eight days.

### Bug 7: Repeated API calls could create duplicate sections

Correction:

Added marker-based replacement and print-sheet recreation.

### Bug 8: Final calculations were available only in the source sheet

Correction:

Final values are now updated in both:

```text
Sheet1
Working_Hours_Print
```

---

## 37. Maintenance Guide

When modifying the application, follow these rules:

### Employee salary changes

Update:

```java
createEmployeeSalaryMap()
```

Keep all employee-name keys lowercase.

### Attendance-row change

Update:

```java
ATTENDANCE_ROW_INDEX
```

Remember that Apache POI row numbering starts from zero.

### First attendance-column change

Update:

```java
FIRST_ATTENDANCE_COLUMN_INDEX
```

### Print group size change

Update:

```java
DAYS_PER_PRINT_BLOCK
```

For example, to display seven days per block:

```java
private static final int DAYS_PER_PRINT_BLOCK = 7;
```

### Worksheet-name change

Update:

```java
SOURCE_SHEET_NAME
PRINT_SHEET_NAME
```

### New attendance status

Update:

```java
checkAndGetUpdatedTime()
```

### Salary-formula change

Update:

```java
processData()
```

### Print-layout change

Update:

```java
createPrintFriendlyWorkingHoursSheet()
```

### Final Excel columns

Update both:

```java
getColumnNameList()
getColumnValueList()
```

The number and order of column names must match the number and order of values.

---

## 38. Troubleshooting

### Problem: Employee salary is not found

Check:

- Employee name in the first row
- Colon after `Emp Name`
- Extra spaces
- Name spelling
- Lowercase salary-map key
- Salary-map value

Expected Excel format:

```text
Emp Name : Employee Name
```

### Problem: `23.58` appears again

Check whether any code is still using:

```java
LocalTime.minusMinutes()
```

Worked duration should be calculated using integer minutes.

### Problem: Salary is lower than expected

Check:

- Per-hour salary
- Monthly decimal-hour conversion
- Break deduction rule
- Holiday hours
- MIS hours
- Advance amount

Do not multiply an `H.mm` display value directly by salary.

### Problem: Working_Hours_Print is not created

Check:

- `WorkingHourCount` was executed
- The workbook is writable
- `Sheet1` exists
- Attendance data is valid
- `CellRangeAddress` is imported

Required import:

```java
import org.apache.poi.ss.util.CellRangeAddress;
```

### Problem: Final calculation does not appear in print sheet

Run the APIs in this order:

```text
Upload
WorkingHourCount
AdvanceSalaryCalculation
Download
```

### Problem: Duplicate methods compile error

Make sure only one version exists for each method:

```java
motiExcelSheet()
processData()
finalSalaryUpdate()
checkAndGetUpdatedTime()
```

Delete older versions after replacing them.

### Problem: Incorrect number of attendance days

Verify:

- Attendance starts from the expected column
- Row 5 contains attendance data
- February’s unused cells are blank
- Non-attendance values are not placed inside the day range
- No attendance exists after day 31

---

## 39. Future Enhancements

Possible future improvements include:

- Move salary data to a database
- Support multiple uploaded employees
- Remove shared static state
- Use `BigDecimal` for payroll
- Add employee ID support
- Add month and year detection
- Validate February leap years
- Calculate duration from punch-in and punch-out
- Support overnight shifts
- Support overtime
- Support half days
- Support paid leave
- Support weekly offs
- Add PDF salary-slip generation
- Email processed salary reports
- Add JWT authentication
- Add role-based access
- Add calculation history
- Add audit logging
- Add unit tests
- Add integration tests
- Add an employee salary master API
- Add frontend progress and validation messages

---

## 40. Final Summary

The application now provides a complete attendance and salary-processing workflow.

It:

1. Uploads an Excel attendance file.
2. Extracts the employee name.
3. Retrieves the hourly salary.
4. Reads daily attendance.
5. Applies a safe 30-minute deduction.
6. Prevents the `23.58` negative-time problem.
7. Calculates monthly totals using minutes.
8. Converts monthly duration into decimal payroll hours.
9. Calculates salary before adjustments.
10. Supports 28, 29, 30, and 31-day months.
11. Creates groups of eight days.
12. Creates an A4 print-friendly worksheet.
13. Applies holiday and MIS hours.
14. Deducts advance salary.
15. Calculates final salary.
16. Updates both the source and print worksheets.
17. Prevents duplicate generated reports.
18. Provides validation and meaningful error messages.

The most important payroll rule is:

```text
Display duration and decimal payroll hours are not the same.
```

Example:

```text
8.30 display duration = 8 hours 30 minutes
8.50 decimal hours    = payroll value for 8 hours 30 minutes
```

Always calculate salary using decimal hours.

---

## Author

```text
Biswajit Sahoo
QA Automation Engineer
```

---

## Document Maintenance

Update this README whenever any of the following changes:

- Excel worksheet structure
- Attendance row location
- Employee-name format
- Attendance statuses
- Break-deduction rules
- Salary-map values
- Salary formulas
- Month-length validation
- API endpoints
- JSON request fields
- Print-sheet layout
- Final summary columns
- Security requirements


original code it is working perfectly but updated code added to the main file enhancement okay

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


//     private static String checkAndGetUpdatedTime(String cellValue){

//         ArrayList<String> al = new ArrayList<String>();

//         String beforespace = "";

//         for(int i = 0; i< cellValue.length(); i++){
//             if(cellValue.charAt(i) == '\n' )
//             {
//                 al.add(beforespace);
//                 beforespace = "";
//             }else{
//                 beforespace = beforespace + cellValue.charAt(i);
//             }
//         }

//         if (!beforespace.isEmpty()) {
//             al.add(beforespace);
//         }

//         String presentAndAbsent = al.get(al.size()-1);
//         String totalTime = al.get(al.size()-2);

//         // Define formatter to parse and format the time in "H:mm" format
//         DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm");

//         // Parse the string to LocalTime
//         LocalTime time = LocalTime.parse(totalTime, formatter);

//         if(presentAndAbsent.equalsIgnoreCase("MIS")){
//             return "MIS";
//         }

//         if(presentAndAbsent.equalsIgnoreCase("P")) {
//             // Check if the time is less than 12:00 (noon)
//             if (time.isBefore(LocalTime.NOON)) {
//                 // Subtract 30 minutes
//                 time = time.minusMinutes(30);
//             }
//         }
//         // Convert back to string for display
//         String updatedTimeString = time.format(formatter);

// //        System.out.println("Updated Time: " + updatedTimeString);
//         return updatedTimeString.replaceAll(":",".");
//     }

    private static String checkAndGetUpdatedTime(String cellValue) {

    if (cellValue == null || cellValue.trim().isEmpty()) {
        return "";
    }

    String[] lines = cellValue.trim().split("\\R");

    if (lines.length < 2) {
        throw new IllegalArgumentException(
                "Invalid cell value. Expected total time and attendance status."
        );
    }

    String presentAndAbsent = lines[lines.length - 1].trim();
    String totalTime = lines[lines.length - 2].trim();

    if (presentAndAbsent.equalsIgnoreCase("MIS")) {
        return "MIS";
    }

    String[] timeParts = totalTime.split(":");

    if (timeParts.length != 2) {
        throw new IllegalArgumentException(
                "Invalid time format: " + totalTime + ". Expected H:mm."
        );
    }

    int hours = Integer.parseInt(timeParts[0]);
    int minutes = Integer.parseInt(timeParts[1]);

    if (hours < 0 || minutes < 0 || minutes > 59) {
        throw new IllegalArgumentException(
                "Invalid duration: " + totalTime
        );
    }

    int totalMinutes = (hours * 60) + minutes;

    if (presentAndAbsent.equalsIgnoreCase("P")) {
        totalMinutes = Math.max(0, totalMinutes - 30);
    }

    int updatedHours = totalMinutes / 60;
    int updatedMinutes = totalMinutes % 60;

    return String.format("%d.%02d", updatedHours, updatedMinutes);
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

        // Name = EmpNameFormat.split(":")[1].trim().toLowerCase();
        Name = EmpNameFormat.split(":")[1].trim().toLowerCase();
        System.out.println(Name);

        Map<String, Double> map = new HashMap<>();
        map.put("dadasaheb kolhe", 118.75);
        map.put("gajanan Raut", 90.00);
        map.put("bhagyavendra singh", 100.00);
        map.put("salim Mohameed", 106.25);
        map.put("alim", 93.75);
        map.put("mahindra", 93.75);
    

        
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(Name)) {
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

