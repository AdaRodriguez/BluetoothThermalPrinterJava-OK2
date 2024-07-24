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

// SQL query to fetch data from multiple tables
$sql = "SELECT 
            transactions.tx_id AS 'TX ID',
            transactions.student_id AS 'STUDENT ID',
            transactions.date AS 'DATE',
            transactions.amt_paid AS 'AMT PAID',
            discount_fee_tbl.title AS 'DISCOUNT',
            transactions.mode_of_tx AS 'MODE OF TX',
            student_tbl.name AS 'NAME'
        FROM transactions
        LEFT JOIN student_tbl ON transactions.student_id = student_tbl.student_id
        LEFT JOIN discount_fee_tbl ON transactions.discount_id = discount_fee_tbl.id";

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
