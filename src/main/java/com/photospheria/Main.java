package com.photospheria;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            System.out.println("Starting Photospheria Level 4 generation...");

            Level4Strategy.execute();

            System.out.println("Level 4 generation complete.");

        } catch (Exception exception) {
            System.err.println("Error generating Level 4 submission:");
            exception.printStackTrace();
            System.exit(1);
        }
    }
}