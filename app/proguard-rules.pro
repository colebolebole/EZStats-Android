# R8 shrinks and optimizes the app's own code along with its libraries. Nothing in the app is reached by
# reflection: the activity and application are kept through the manifest, and the ViewModel's constructor by
# the lifecycle library's own rules.
