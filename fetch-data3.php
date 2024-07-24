<?php
// Database connection parameters
$servername = "enrol.lesterintheclouds.com";
$username = "apc81te4amz9";
$password = "a4C9fqJ37$!n";
$dbname = "db_enrolment_app";

// Create connection
$conn = new mysqli($servername, $username, $password, $dbname);

// Check connection
if ($conn->connect_error) {
    die("Connection failed: " . $conn->connect_error);
}

// SQL query to fetch data from transaction_tbl and student_tbl
$sql = "SELECT 
            t.transaction_date AS 'DATE',
            t.amount_paid AS 'AMT PAID',
            s.name AS 'NAME',
            s.student_id AS 'STUDENT ID'
        FROM transaction_tbl AS t
        LEFT JOIN student_tbl AS s ON t.student_id = s.id";

$result = $conn->query($sql);

$data = array();

if ($result->num_rows > 0) {
    while ($row = $result->fetch_assoc()) {
        $data[] = $row;
    }
}

echo json_encode($data);

$conn->close();
?>
