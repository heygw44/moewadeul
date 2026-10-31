DELETE a FROM program_vacancy_alert a
JOIN member m ON m.id = a.member_id
WHERE m.status = 'WITHDRAWN';
