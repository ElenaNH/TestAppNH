package ru.netology.nmedia.util

sealed class ConsolePrinter() {

    companion object ConsolePrinter {
        private var printingTurnedOn: Boolean = true  // true - печать включена

        fun printText(text: String) {
            val thisObj = this::class.simpleName // this::class.java.declaringClass
            if (printingTurnedOn) println("+++$thisObj+++ $text")
        }

        fun printEmpty() {
            if (printingTurnedOn) println()
        }

        fun TurnOn() {
            printingTurnedOn = true
        }

        fun TurnOff() {
            printingTurnedOn = false
        }

    }

}
