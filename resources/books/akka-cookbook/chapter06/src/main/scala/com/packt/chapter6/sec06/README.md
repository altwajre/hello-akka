One important detail to point out is the possibility of forcing given persistent actors to use specific plugins for 
journals and snapshots that are different from those defined in reference.conf. 

This allows some flexibility if you want to use different storage plugins for different actors.

It can be done by overriding journalPluginId, snapshotPluginId, or both in the persistent actors:


trait GivenActorWithSpecificPlugins extends PersistentActor {
    override val persistenceId = "some-id"
    // Absolute path to the journal plugin configuration entry in the `reference.conf`.
    override def journalPluginId = "some-journal-plugin-id"
    // Absolute path to the snapshot store plugin configuration entry in the `reference.conf`.
    override def snapshotPluginId = "some-snapshot-plugin-id"
}