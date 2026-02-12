#!/bin/bash

DB_NAME="dapm_security"
DB_USER="postgres"
DB_PASSWORD="1234"  # Make sure this matches your Spring Boot config
DB_HOST="localhost"
DB_PORT="5432"

export PGPASSWORD="$DB_PASSWORD"  # Avoid password prompt

echo "Resetting database: $DB_NAME..."

# Check if PostgreSQL is running
pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER"
if [ $? -ne 0 ]; then
    echo "Error: PostgreSQL is not running on $DB_HOST:$DB_PORT"
    exit 1
fi

# Drop the database if it exists
echo "Checking if database '$DB_NAME' exists..."
if psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -lqt | cut -d \| -f 1 | grep -qw "$DB_NAME"; then
    echo "Database '$DB_NAME' exists. Dropping..."
    dropdb -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" "$DB_NAME"
else
    echo "Database '$DB_NAME' does not exist. Creating a new one."
fi

# Create a new database owned by postgres
echo "Creating database '$DB_NAME' with owner '$DB_USER'..."
createdb -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -O "$DB_USER" "$DB_NAME"

# Verify database creation
echo "Verifying database creation..."
psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -c "\l" | grep "$DB_NAME"

echo "Database '$DB_NAME' has been reset successfully!"
