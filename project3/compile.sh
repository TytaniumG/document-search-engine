#!/bin/bash

# Project 1 - Compilation Script

echo "=== Compiling Project 1 ==="
echo ""

# Create bin directory if it doesn't exist
mkdir -p bin

# Compile source files
echo "Compiling source files..."
javac -d bin src/*.java

if [ $? -eq 0 ]; then
    echo "✓ Source files compiled successfully"
else
    echo "✗ Source compilation failed"
    exit 1
fi

# Compile test files (if JUnit is available)
if [ -d "test" ]; then
    echo ""
    echo "Compiling test files..."
    
    # Check if JUnit is available
    if [ -f "lib/junit-4.13.2.jar" ]; then
        javac -cp "bin:lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar" -d bin test/*.java
        
        if [ $? -eq 0 ]; then
            echo "✓ Test files compiled successfully"
        else
            echo "✗ Test compilation failed"
            exit 1
        fi
    else
        echo "⚠ JUnit not found in lib/ directory"
        echo "  To compile tests, download:"
        echo "  - junit-4.13.2.jar"
        echo "  - hamcrest-core-1.3.jar"
        echo "  and place them in lib/ directory"
    fi
fi

echo ""
echo "=== Compilation Complete ==="
echo ""
echo "To run the benchmark:"
echo "  java -cp bin Benchmark"
echo ""
echo "To run provided tests:"
echo "  java -cp bin:lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar org.junit.runner.JUnitCore ProvidedTests"
echo ""
echo "To run your tests:"
echo "  java -cp bin:lib/junit-4.13.2.jar:lib/hamcrest-core-1.3.jar org.junit.runner.JUnitCore StudentTests"
