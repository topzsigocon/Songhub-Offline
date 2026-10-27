// Gamit ang library para mas precise
fun parseWithLibrary(file: File): List<String> {
    val midiFile = com.leff.midi.MidiFile(file)
    val result = mutableListOf<String>()
    for (track in midiFile.tracks) {
        for (event in track.events) {
            if (event is com.leff.midi.event.meta.Lyrics) {
                result.add(event.lyric)
            }
            if (event is com.leff.midi.event.meta.Text) {
                // minsan Text event ang gamit hindi Lyrics
                if (event.text.length < 80) result.add(event.text)
            }
        }
    }
    return result
}
