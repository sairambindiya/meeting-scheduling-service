INSERT INTO users (name, email)
VALUES
    ('Bindu Murulidhar', 'bindu@example.com'),
    ('Sindhu Kiran', 'sindhu@example.com'),
    ('Spoorthi Ganesh', 'spoorthi@example.com');

INSERT INTO calendars (user_id)
SELECT id
FROM users
WHERE email IN (
    'bindu@example.com',
    'sindhu@example.com',
    'spoorthi@example.com'
);