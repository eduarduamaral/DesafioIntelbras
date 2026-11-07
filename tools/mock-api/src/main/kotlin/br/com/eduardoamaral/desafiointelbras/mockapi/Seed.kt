package br.com.eduardoamaral.desafiointelbras.mockapi

// Alguns dispositivos para a lista não começar vazia. Ids fixos: o reset volta ao mesmo estado.

fun seedVideos(): List<VideoDevice> = listOf(
    VideoDevice("seed-video-1", "Câmera da garagem", "ABC123DEF4567", "admin", "admin123"),
    VideoDevice("seed-video-2", "Câmera do portão", "XYZ987WVU6543", "operador", "senha123"),
)

fun seedAlarms(): List<AlarmCentral> = listOf(
    AlarmCentral("seed-alarm-1", "Central do escritório", "1A2B3C4D5E6F", "1234"),
)
