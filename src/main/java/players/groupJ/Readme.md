# Group J - Readme

## Agent Set Up

The code is in the folder 'groupJ', same as this Readme file. To set up the agent, drag the entire folder 'groupJ' into 'src/main/java/players'. The three files should then be under 'src/main/java/players/groupJ/GroupJ\_[...].java'. Then, simply add a new `GroupJ_BasicMCTSPlayer` instance like so:

    agents.add(new GroupJ_BasicMCTSPlayer());

We also already set the `budgetType` of `BUDGET_TIME` and the budget of 1000ms for the competition. This change is made within the constructor of 'GroupJ_BasicMCTSPlayer.java'. If you would like to change that for some reason, that's where you can find it.
