def server(type, title) {
    def cis = configurationApi.searchByTypeAndTitle(type, title)
    if (cis.isEmpty()) {
        throw new RuntimeException("No CI found for the type '${type}' and title '${title}'")
    }
    if (cis.size() > 1) {
        throw new RuntimeException("More than one CI found for the type '${type}' and title '${title}'")
    }
    cis.get(0)
}
//TODO ask Xebia abouth the get prod versions task issue
// TODO fix these to not be hard coded.....
//TODO add that tag update that I do at the end of the nonprod phases task tp tasg the release wiht the app codes in the release
def xldeployServer1 = server('xldeploy.Server','XL Deploy - custom')
def servicenowServer1 = server('servicenow.Server','SN')
def xlreleaseServer1 = server('KidSetup.XLRServer','XLR')
//get values from the source release
def fullApplicationPaths = releaseVariables['fullApplicationPaths']
def ci_code = releaseVariables['ci_code']
def ci = releaseVariables['ci']
def isQASRequired = releaseVariables['isQASRequired']
def isCodeReviewRequired = releaseVariables['isCodeReviewRequired']
def manualNonProdDeploys = releaseVariables['manualNonProdDeploys']
def maxPackages = releaseVariables['maxPackages']
def releaseType = releaseVariables['releaseType']
def deployInSameChangeWindow = releaseVariables['deployInSameChangeWindow']
def prChangeId = releaseVariables['ChangeId']
def pvChangeID = releaseVariables['pvChangeID']
def incidentID = releaseVariables['incidentID']
def pipeline = releaseVariables['pipeline']
def useRecoveryManager = releaseVariables['useRecoveryManager']
def envList = releaseVariables['envList']
def releaseName = release.title
def releaseOwner = release.owner
def byPassInc = false
def pauseDeploymentOnFail = false
//global variables
def admEmail = globalVariables['global.AdmEmail']
def changeApprovalPhases = globalVariables['global.ChangeApprovalPhases']
def sm9PollInterval = globalVariables['global.SM9PollInterval']
//feature flags
def ffUseAutomatedApprovalCheck = globalVariables['global.ffUseAutomatedApprovalCheck']
def ffUseCodeDeployTaskForStartTime = globalVariables['global.ffUseCodeDeployTaskForStartTime']
//initialize some variables for use below
def pvFlag = false
def qasTeam = 'Application Test Services'
def useStandardChange = false
def deployToDr = false
def prEnv = "None"
def drEnv = "None"
def pvEnv = "None"
def isXld = true
def changeIdPreCondition = "changeID = getCurrentRelease().variablesByKeys['ChangeId']\nif changeID.value:\n\tresult = True\nelse:\n\tresult = False"
def pvChangeIdPreCondition = "changeID = getCurrentRelease().variablesByKeys['pvChangeID']\nif changeID.value:\n\tresult = True\nelse:\n\tresult = False"
def incPreCondition = "incidentID = getCurrentRelease().variablesByKeys['incidentID']\nif incidentID.value:\n\tresult = True\nelse:\n\tresult = False"

//get the team membership to determine who is on the team - specifically, which AD group, so that we can set then to be watchers later
def theTeams = releaseApi.getTeams(release.id)
def tla00Watchers = []

for (theTeam in theTeams) {
	if (theTeam.teamName == 'tla00') {
		for (theMember in theTeam.members) {
			tla00Watchers.add(theMember.name)
		}
	}
}

def tla00_app_supportWatchers = []

for (theTeam in theTeams) {
	if (theTeam.teamName == 'tla00_app_support') {
		for (theMember in theTeam.members) {
			tla00_app_supportWatchers.add(theMember.name)
		}
	}
}

try {
    byPassInc = releaseVariables['byPassInc']
}
catch(Exception e) {
    byPassInc = false
}

try {
    pauseDeploymentOnFail = releaseVariables['pauseDeploymentOnFail']
}
catch(Exception e) {
    pauseDeploymentOnFail = false
}


xlr {
    release(releaseName) {
	    tags 'Standard Template: 2.0.0'
	    scriptUsername 'XLRSCRIPT'
	    scriptUserPassword '{b64}Kw5wCGK5OUJmWcuKvSsRIA=='
	    autoStart(true)
	    owner releaseOwner
    	//TODO set the release owner
    	//TODO add watchers
        variables {
            // kid setup variables
            stringVariable("ci_code") {
                description("The 5 character CI Code being deployed")
                label("CI Code")
                required(true)
                value ci_code
            }
            stringVariable("ci") {
                description("CI from SM9 or ServiceNow")
                label("CI")
                required(true)
                value ci
            }
            booleanVariable("isQASRequired") {
                description("Please check this box to indicate that QAS approval is required for this release.")
                label("Is QAS Required?")
                required(false)
                value(isQASRequired)
            }
            booleanVariable("isCodeReviewRequired") {
                description("Check this box to make code review required")
                label("Is code review required?")
                required(false)
                value (isCodeReviewRequired)
            }
            // booleanVariable("manualNonProdDeploys") {
            //     description("Check this box to have a manual task added prior to the non prod deployments")
            //     label("Use manual non prod deploys?")
            //     required(false)
            //     value (manualNonProdDeploys)
            // }
            stringVariable("maxPackages") {
                description("This overrides the global variable setting.")
                label("Maximum number of packages to return from XLD")
                required(false)
                value maxPackages
            }
            stringVariable("releaseType") {
                description("If this is an EMERGENCY change, then please select EMERGENCY")
                label("Please select the type of release:")
                required(true)
                value releaseType
            }
            stringVariable("deployInSameChangeWindow") {
                description("Will the Preview and Production deployments occur in the same change window?")
                label("Will the Preview and Production deployments occur in the same change window?")
                required(false)
                value (deployInSameChangeWindow)
            }
            stringVariable("ChangeId") {
                description("Please enter the ID of the Change Record associated with this release.")
                label("Change ID")
                required(true)
                value (prChangeId)
            }
            stringVariable("pvChangeID") {
                description("The Id of the change record associated with the deployment to the preview environment")
                label("Preview Change ID")
                required(true)
                value (pvChangeID)
            }
            if (releaseType == 'Emergency' || useRecoveryManager == 'Yes'){
				stringVariable("incidentID") {
					description("Please enter the ID of the Incident related to this release.")
					label("Incident ID")
					required(true)  
					value (incidentID)
				}         	
            }
            else {
	            stringVariable("incidentID") {
	                description("Please enter the ID of the Incident related to this release.")
	                label("Incident ID")
	                required(false)
	                value (incidentID)
	            }
            }
            stringVariable("theReleaseId") {
                description("The release id")
                label("Release ID")
                required(false)
            }
            // listVariable("fullApplicationPaths") {
            //     description("Please delete any that are not needed.")
            //     label("Objects to be deployed (delete any that are not needed)")
            //     required(true)
            //     //value fullApplicationPaths
            // }               
            stringVariable("pipeline") {
                description("This is the XLD pipeline that will be used for the release.")
                label("Please select an XLD Pipeline:")
                required(true)
                value (pipeline)
            }
            stringVariable("useRecoveryManager") {
                label("Will this be a Recovery Manager Override scenario?")
                required(false)
                value (useRecoveryManager)
                }
            // variables used by the release
            listBoxVariable("envListbox") {
                label("Environment List for Selection:")
                required(true)
            }
            listVariable("codeDevelopers") {
                description("Please list the code developers working on this release.")
                label("Code Developers for this release:")
                required(true)
            }  
            mapVariable("appToPackageMap") {
                description("This shows which packages were deployed with this release.")
                label("Application to package mapping")
                required(false)
            }
            listBoxVariable("versionListbox") {
                description("The list of versions by application in XLD")
                label("Please select a version to be deployed")
                required(true)
                possibleValues "NA"
            }
            stringVariable("changeStartTime") {
                description("The start time of the production change")
                label("Production Deployment Change Start Time")
                required(false)
            }
            stringVariable("changeCategory") {
                description("The Production Change Category")
                label("Change Category")
                required(false)
            }
            stringVariable("pvchangeCategory") {
                description("The Preview Change Category")
                label("Preview Change Category")
                required(false)
            }
            stringVariable("changeStartTimepv") {
                description("Preview Deployment Change Start Time")
                label("The start time of the preview change")
                required(false)
            }
            stringVariable("changeEndTime") {
                description("Production Deployment Change End Time")
                label("The end time of the production change")
                required(false)
            }
            stringVariable("changeEndTimepv") {
                description("Preview Deploymemt Change End Time")
                label("The end time of the preview change")
                required(false)
            }
            stringVariable("incidentPriority") {
                description("The priority of the Incident")
                label("Incident priority")
                required(false)
            }
            stringVariable("codeDeployStartTime") {
                description("The time for the code deployment to start in the format yyyy-mm-dd HH:mm:ss (2018-03-20 21:15:00)")
                label("Code Deployment Start Time")
                required(true)
            }
            stringVariable("codeReviewCompletedBy") {
                description("The code review was completed by")
                label("The code review was completed by:")
                required(false)
            }
            stringVariable("codeReviewStatus") {
                description("The code review status")
                label("The code review status:")
                required(false)
            }
            stringVariable("codeReviewCompleteDate") {
                description("The code review complete date")
                label("The code review complete date:")
                required(false)
            }
            stringVariable("qasTaskStatus") {
                description("This will show skipped if QAS has skipped any of the approvals, else, it will show COMPLETED")
                label("QAS Approval Status")
                required(false)
            }
            listVariable("prodDeployTimes") {
                description("The times of the prod deployments")
                label("Prod Deploy Times")
                required(false)
            }
            stringVariable("pvChangeCI") {
                description("The CI of the Preview CR")
                label("Preview CR CI")
                required(false)
            }
            stringVariable("pvChangePhase") {
                description("The phase of the Preview CR")
                label("Preview CR phase")
                required(false)
            }
            stringVariable("ChangeCI") {
                description("The CI of the Production CR")
                label("Production CR CI")
                required(false)
            }
            stringVariable("ChangePhase") {
                description("The phase of the Production CR")
                label("Production CR phase")
                required(false)
            }
            listBoxVariable("scheduleDeploy") {
                description("If you select Yes, the Change Start Time will be used as the Deploy to Prod start time. You can manually update this time in the Deploy to Prod task.")
                label("Should the Deploy to Preview / Prod steps be scheduled to run automatically?")
                possibleValues 'Yes','No'
                required(true)
            }
            // booleanVariable("prod") {
            //     description("Indicates if the release will go all the way to production")
            //     label("Prod Release?")
            //     required(false)
            // }
            listVariable("envListOutput") {
                description("The list of environments")
                label("Environment List")
                required(false)
            }
            mapVariable("prodVersions") {
                description("The map of prod versions by applications")
                label("Prod Version List")
                required(false)
            }
            stringVariable("chgApproval") {
                description("The change approval status")
                label("Change Approval Status")
                required(false)
            }
            stringVariable("trlFile") {
                description("Enter filename to download")
                label("Trillium Filename")
                required(true)
            }
            stringVariable("readyToDeploy") {
                description("The ready to deploy status")
                label("Ready to Deploy Status")
                required(false)
            }
            // booleanVariable("pvFlag") {
            //     description("Indicates if a preview environment is used")
            //     label("Preview Environment?")
            //     required(false)
            // }
//Final Manual Check
            listBoxVariable("confirmExecution") {
                description("Have you confirmed that the Change Record is a Standard Change and is in the Execution Phase?")
                label("Have you confirmed that the Change Record is a Standard Change and is in the Execution Phase?")
                possibleValues 'Yes','No'
                required(true)
            }
            listBoxVariable("confirmApproved") {
                description("Have you confirmed that the Change Record is approved?")
                label("Have you confirmed that the Change Record is approved?")
                possibleValues 'Yes','No'
                required(true)
            }        
            listBoxVariable("confirmCodeReview") {
                description("Have you confirmed that the code review was completed by someone other than a code developer?")
                label("Have you confirmed that the code review was completed by someone other than a code developer?")
                possibleValues 'Yes','No'
                required(true)
            }   
            listBoxVariable("confirmCiMatchespv") {
                description("Have you confirmed that the CI for the release matches with the change record?")
                label("Have you confirmed that the CI for the release matches with the change record?")
                possibleValues 'Yes','No'
                required(true)
            }         
            listBoxVariable("confirmCiMatches") {
                description("Have you confirmed that the CI for the release matches with the change record?")
                label("Have you confirmed that the CI for the release matches with the change record?")
                possibleValues 'Yes','No'
                required(true)
            }   
            listBoxVariable("releaseChgInfo") {
                description("Has a Code Deployment task been created and populated with the Release Number?")
                label("Has a Code Deployment task been created and populated with the Release Number?")
                possibleValues 'Yes','No'
                required(true)
            }   
            listBoxVariable("confirmChgWindow") {
                description("Have you confirmed that the deployment will fall inside the change window?")
                label("Have you confirmed that the deployment will fall inside the change window?")
                possibleValues 'Yes','No'
                required(true)
            }   
// QAS
            listBoxVariable("functionTestYesNo") {
                label("Has an overall functional test been completed on the application in the QV environment?")
                possibleValues 'Yes','No'
                required(true)
            }   
            listBoxVariable("automatedTestYesNo") {
                label("Have automated testing scripts been run on the application?")
                possibleValues 'Yes','No'
                required(true)
            }   
            listBoxVariable("perfTestYesNo") {
                label("Has a performance test been run on the application in the QV environment?")
                possibleValues 'Yes','No'
                required(true)
            }   
            listBoxVariable("secTestYesNo") {
                label("Has a security test been run on the code base?")
                possibleValues 'Yes','No'
                required(true)
            }   
            listBoxVariable("secTestVulnerabilities") {
                label("Were there vulnerabilities? If so, please provide variance numbers.")
                possibleValues 'Yes','No'
                required(true)
            }  
            stringVariable("functionalTestComments") {
                label("Please explain why no functional testing was executed:")
                required(true)
            }
            stringVariable("functionalTestDate") {
                label("Please include the date of the functional test:")
                required(true)
            }
            stringVariable("automatedTestingComments") {
                label("Please explain why no automated testing was executed:")
                required(true)
            }
            stringVariable("automatedTestingDate") {
                label("Please include the date of the automated test:")
                required(true)
            }
            stringVariable("performanceTestComments") {
                label("Please explain why a performance test was not executed:")
                required(true)
            }
            stringVariable("performanceTestDate") {
                label("Please include the date of the performance test:")
                required(true)
            }
            stringVariable("securityComments") {
                label("Please explain why a security test was not executed:")
                required(true)
            }               
            listVariable("securityVarianceNumbers") {
                label("Please list Security variance numbers:")
                required(true)
            }
            stringVariable("securityTestDate") {
                label("Please include the date of the security test:")
                required(true)
            } 
//PIR variables  
            listBoxVariable("exceptionViolation") {
                label("Exception / Violation")
                possibleValues 'Clean','Exception','Violation'
                required(true)
            }  
            listBoxVariable("confirmDeploymentTime") {
                label("Have you confirmed that the deployment was started inside the change window?")
                possibleValues 'Yes','No'
                required(true)
            }   
            stringVariable("reviewSummary") {
                label("Review Summary:")
                required(true)
            }      
            stringVariable("exceptionViolationReason") {
                label("Exception/Violation Reason:")
                required(true)
            }  
            listBoxVariable("recoveryMgr") {
                label("Recovery Manager Name")
                possibleValues variable('global.recoveryManagerList')
                required(true)
            }      
        }// end variables
        //description "Hello world release description"
        phases {
        	phase {
        		title "XLR Initialize"
        		tasks {
        			custom('Add Tags to the Release'){
        				script {
        					type 'releaseScripts.addReleaseTags'
                            theReleaseId variable("theReleaseId")
        				}
        			}
        		}
        	}
            if (releaseType == 'Emergency' && useRecoveryManager == 'No' && !byPassInc){
                phase {
                    title "Emergency Phase"
                    tasks {
                        userInput('Please input the incident information for the Production Deployment') {
                            variables {
                            	variable "incidentID"
                                }
                            team "tla00"
                            tags "no skip"
                        }
	                    custom("Get Incident Details from ServiceNow"){
	                        precondition incPreCondition
	                        script {
	                            type 'servicenow.GetIncidentDetail'
	                            servicenowServer servicenowServer1
	                            incidentId variable("incidentID")
	                            incidentPriority variable("incidentPriority")
	                        }
	                    }                        
	                    custom("Tag release with Incident ID"){
	                        precondition incPreCondition
	                        script {
	                            type "releaseScripts.tagRelease"
	                            value variable("incidentID")
                                label " "
	                        }
	                    }
	                    // custom("Tag release with Incident Priority"){
	                    //     precondition "incidentPriority = getCurrentRelease().variablesByKeys['incidentPriority']\nif incidentPriority.value:\n\tresult = True\nelse:\n\tresult = False"
	                    //     script {
	                    //         type "releaseScripts.tagRelease"
	                    //         value variable("incidentPriority")
	                    //         label "Incident Priority"
	                    //     }
	                    // }
                    }
                }
            } //end emergency release
            if (useRecoveryManager == 'Yes'){
                phase {
                    title "Emergency Phase"
                    tasks {
                        //TODO need to make incident ID required
                        userInput('Input Recovery Manager') {
                            variables {
                                variable "recoveryMgr"
                                }
                            team "tla00"
                            tags "no skip"
                        }
                    }
                }
            }//end use recovery manager
            phase {
                title "Package"
                tasks {
                    manual("Complete this task when all packages are built and available in XL Deploy") {
                        team "tla00"
                    }
                    for (appCode in fullApplicationPaths) { //create these tasks for each app code that we are deploying
                        custom('XLD task to get all versions for ' + appCode) {
                            script {
                              type 'xldeploy.GetAllVersionsTask'
                              xldeployServer xldeployServer1
                              applicationId appCode
                            }
                        }
                        userInput('Please select a package to Deploy for ' + appCode) {
                            variables {
                              variable "versionListbox"
                            }
                            team "tla00"
                        }
                        custom('populate non prod tasks') {
                            script {
                              type 'releaseScripts.updateDeployTasks'
                              appName appCode
                            }
                        }
                    }    //end if
                    if (useRecoveryManager == 'No'){
                        if (releaseType == 'Normal'){
                            if (isCodeReviewRequired){
                                userInput('Please enter the code developer for the release') {
                                    variables {
                                      variable "codeDevelopers"
                                        }
                                    team "tla00"
                                    tags "no skip"
                                    watchers tla00Watchers
                                }
                            }
                         }
                    }//end code developer task
                }//end tasks
            } //end package phase
            for (env in envList){
            	//get the last 2 characters of the environment as the pretty one....dr, pr, pv, etc.
                def prettyEnv = env.reverse().take(2).reverse()
                if (prettyEnv == 'pv') {
                    pvFlag = true
                    pvEnv = env
                }
                if (prettyEnv == 'dr'){
                    deployToDr = true
                    drEnv = env
                }
                if (prettyEnv == 'pr'){
                    prEnv = env
                }
                if (prettyEnv != 'pr' && prettyEnv != 'dr' && prettyEnv != 'pv'){//for the non prod environments, create the phases and tasks
                    phase {
                        title prettyEnv + " - phase"
                        tasks {
                            if (manualNonProdDeploys){
                                manual("Complete this task to start the deployment to " + prettyEnv) {
                                    team "tla00"
                                    }
                                }
                            for (appCode in fullApplicationPaths) {                            
                                custom('Deploy ' + appCode + ' to ' + prettyEnv) {
                                    tags "no skip", prettyEnv, appCode
                                    script {
                                        type 'xldeploy.DeployTask'
                                        xldeployServer xldeployServer1
                                        continueIfStepFails false
                                        environment env
                                        if (pauseDeploymentOnFail) {
                                            rollbackOnError false
                                            cancelOnError false
                                        }
                                    }
                                team "tla00"
                                }
                            }
                            gate ("Validate " + prettyEnv) {//only create one gate task per phase
                                description "Complete this task to confirm that validation has been completed. Skip this task if validation was not completed. Please note that a skipped task may be escalated for approval prior to approving the production release."
                                tags "no skip"
                                team "tla00"
                            }
                        } //end tasks
                    } //end phase                       
                } // end if
            }  //end for
            if (prEnv != "None"){
                phase {
                    title "Post Non Prod"
                    tasks {
                        //TODO need to make change ID required
                        custom('check for skips') {
                            script {
                              type 'releaseScripts.checkForSkips'
                              email admEmail
                                }
                            }
                        }
                    } 
                phase {
                    title "Pre-Production"
                    tasks {
                        if (useRecoveryManager == 'No') {
                            if (!pvFlag || releaseType == 'Emergency' || deployInSameChangeWindow == 'Yes') {//most releases will go thorugh this section
                                userInput('Please input change and incident information for the Production Deployment') {
                                    variables {
                                      variable "ChangeId"
                                      variable "incidentID"
                                      variable "scheduleDeploy"
                                        }
                                    team "tla00"
                                    tags "no skip"
                                }
                                custom("Get Change Details from ServiceNow"){
                                    precondition changeIdPreCondition
                                    script {
                                        type 'servicenow.GetChangeDetail'
                                        servicenowServer servicenowServer1
                                        changeId variable("ChangeId")
                                        changeCategory variable("changeCategory")
                                        plannedStartDt variable("changeStartTime")
                                        plannedEndDt variable("changeEndTime")
                                        phase variable("ChangePhase")
                                        primaryCI variable("ChangeCI")
                                        approval variable("chgApproval")
                                        readyToDeploy variable("readyToDeploy")
                                    }
                                }
                                custom("Tag release with Change ID"){
                                    precondition changeIdPreCondition
                                    script {
                                        type "releaseScripts.tagRelease"
                                        value variable("ChangeId")
                                        label " "
                                    }
                                }
                                custom("Tag release with Change Category"){
                                    precondition "changeCategory = getCurrentRelease().variablesByKeys['changeCategory']\nif changeCategory.value:\n\tresult = True\nelse:\n\tresult = False"
                                    script {
                                        type "releaseScripts.tagRelease"
                                        value variable("changeCategory")
                                        label " "
                                    }
                                }
                            }// end !pvFlag || releaseType == 'Emergency' || deployInSameChangeWindow == 'Yes'
                            else {//for preview deployments not occurring in the same changes window.....we will need 2 CRs, so we get the preview CR first
                                userInput('Please input change and incident information for the Preview Deployment') {
                                    variables {
                                      variable "pvChangeID"
                                      variable "incidentID"
                                      variable "scheduleDeploy"
                                        }
                                    team "tla00"
                                    tags "no skip"
                                }
                                custom("Get Change Details from ServiceNow"){
                                    precondition pvChangeIdPreCondition
                                    script {
                                        type 'servicenow.GetChangeDetail'
                                        servicenowServer servicenowServer1
                                        changeId variable("pvChangeID")
                                        changeCategory variable("pvchangeCategory")
                                        plannedStartDt variable("changeStartTimepv")
                                        plannedEndDt variable("changeEndTimepv")
                                        phase variable("pvChangePhase")
                                        primaryCI variable("pvChangeCI")
                                        approval variable("chgApproval")
                                        readyToDeploy variable("readyToDeploy")
                                    }
                                } 
                                custom("Tag release with Change ID"){
                                    precondition pvChangeIdPreCondition
                                    script {
                                        type "releaseScripts.tagRelease"
                                        value variable("pvChangeID")
                                        label " "
                                    }
                                }
                                custom("Tag release with Change Category"){
                                    precondition "changeCategory = getCurrentRelease().variablesByKeys['pvchangeCategory']\nif changeCategory.value:\n\tresult = True\nelse:\n\tresult = False"
                                    script {
                                        type "releaseScripts.tagRelease"
                                        value variable("pvchangeCategory")
                                        label " "
                                    }
                                } 
                            } //end else
                            custom("Get Incident Details from ServiceNow"){
                                precondition incPreCondition
                                script {
                                    type 'servicenow.GetIncidentDetail'
                                    servicenowServer servicenowServer1
                                    incidentId variable("incidentID")
                                    incidentPriority variable("incidentPriority")
                                }
                            }                        
                            custom("Tag release with Incident ID"){
                                precondition incPreCondition
                                script {
                                    type "releaseScripts.tagRelease"
                                    value variable("incidentID")
                                    label " "
                                }
                            }
                            // custom("Tag release with Incident Priority"){
                            //     precondition "incidentPriority = getCurrentRelease().variablesByKeys['incidentPriority']\nif incidentPriority.value:\n\tresult = True\nelse:\n\tresult = False"
                            //     script {
                            //         type "releaseScripts.tagRelease"
                            //         value variable("incidentPriority")
                            //         label "Incident Priority"
                            //     }
                            // }
                            if (releaseType == 'Normal'){
                                //Code Review Questions
                                if (isCodeReviewRequired || isQASRequired){//we need a container if either are true
                                    parallelGroup('Review Gates'){//a container to hold all of these tasks and we need the tasks to run in parallel so code review and qas can be done at the same time
                                        tasks {
                                            if (isCodeReviewRequired){//code review tasks
                                                sequentialGroup('Code Review Group'){
                                                    tasks{
                                                        custom("Tag release with Code Review Eligible"){
                                                            script {
                                                                type "releaseScripts.tagRelease"
                                                                value "Eligible"
                                                                label "Code Review"
                                                            }
                                                        }
                                                        gate('Code Review Questions'){
                                                            //need to get the watchers list for this task
                                                            team "tla00_app_support"
                                                            tags "no skip"
                                                            watchers tla00_app_supportWatchers
                                                            conditions{
                                                                condition('Confirm that code does not implement vulnerabilities that could be exploited later')
                                                                condition('Confirm that code does not implement changes that could compromise customer or employee privacy')
                                                                condition('Confirm that code does not inappropriately modify customer, account, or transaction information')
                                                            }
                                                        }
                                                        custom('Get Code Review Task Info'){
                                                            script{
                                                                type "releaseScripts.getTaskInfo"
                                                                xlreleaseServer xlreleaseServer1
                                                                taskTitle "Code Review Questions"
                                                                taskOwner variable('codeReviewCompletedBy')
                                                                taskStatus variable('codeReviewStatus')
                                                                taskCompleteDate variable('codeReviewCompleteDate')
                                                            }
                                                        }
                                                        custom("Remove tag from release - Code Review Eligible"){
                                                            script {
                                                                type "releaseScripts.deleteReleaseTag"
                                                                delTag "Code Review: Eligible"
                                                            }
                                                        }
                                                        custom("Tag release with Code Review Complete"){
                                                            script {
                                                                type "releaseScripts.tagRelease"
                                                                value variable('codeReviewStatus')
                                                                label "Code Review"
                                                            }
                                                        }
                                                    }//end tasks
                                                }// end code review group
                                            }//end if code review required
                                            if (isQASRequired){
                                                sequentialGroup('Ask QAS Questions'){//continaer to run the included containers sequentially
                                                    tasks{
                                                        parallelGroup('Ask Yes / No Questions'){//run these in parallel so they can be completed at the same time
                                                            tasks{
                                                                userInput('Has security testing been executed?') {
                                                                    variables {
                                                                      variable "secTestYesNo"
                                                                        }
                                                                    team qasTeam
                                                                    tags "no skip"
                                                                    watchers ('APP_XLR_QAS')
                                                                }
                                                                userInput('Has performance testing been executed?') {
                                                                    variables {
                                                                      variable "perfTestYesNo"
                                                                        }
                                                                    team qasTeam
                                                                    tags "no skip"
                                                                    watchers ('APP_XLR_QAS')
                                                                }
                                                                userInput('Has automated testing been executed?') {
                                                                    variables {
                                                                      variable "automatedTestYesNo"
                                                                        }
                                                                    team qasTeam
                                                                    tags "no skip"
                                                                    watchers ('APP_XLR_QAS')
                                                                }
                                                                userInput('Has functional Testing been executed?') {
                                                                    variables {
                                                                      variable "functionTestYesNo"
                                                                        }
                                                                    team qasTeam
                                                                    tags "no skip"
                                                                    watchers ('APP_XLR_QAS')
                                                                }                                                                                                                                                
                                                            }
                                                        }
                                                        custom("Script to ask follow up QAS questions"){
                                                            script{
                                                                type "releaseScripts.qasQuestions"
                                                            }
                                                        }
                                                        parallelGroup('QAS Follow Up Questions'){

                                                        }
                                                    }
                                                }//qas group
                                            }// end QAS                                    
                                        }//end tasks
                                    }//end review gates
                                }//end if code review or qas required
                                sequentialGroup("Check for skips group"){
                                    tasks{
                                        custom('check for skips'){
                                            script{
                                                type 'releaseScripts.checkForSkips'
                                                email admEmail
                                            }
                                        }
                                    }
                                }
                            } //end normal release block
                            // Final manual check and scheduling
                            if (releaseType == 'Emergency'){
                                userInput('Final Manual Check and Schedule Deployment'){
                                    variables{
                                        variable "ChangeId"
                                        variable "confirmChgWindow"
                                        variable "changeStartTime"
                                        variable "changeEndTime"
                                        variable "scheduleDeploy"
                                        variable "releaseChgInfo"
                                        variable "ci_code"
                                        variable "confirmCiMatches"
                                        variable "incidentPriority"
                                    }
                                    team "EOC"
                                }
                            }
                            else if (pvFlag && deployInSameChangeWindow == 'No' && useStandardChange) {
                                // put some stuff in here
                                custom("Change Approval Check SN"){
                                    script {
                                        type 'servicenow.CheckChangeApproval'
                                        servicenowServer servicenowServer1
                                        changeId variable("pvChangeID")
                                        varChangePhase changeApprovalPhases
                                        pollInterval sm9PollInterval
                                    }
                                }  
                                custom("Get Change Details from ServiceNow"){
                                    precondition changeIdPreCondition
                                    script {
                                        type 'servicenow.GetChangeDetail'
                                        servicenowServer servicenowServer1
                                        changeId variable("pvChangeID")
                                        changeCategory variable("pvchangeCategory")
                                        plannedStartDt variable("changeStartTimepv")
                                        plannedEndDt variable("changeEndTimepv")
                                        phase variable("pvChangePhase")
                                        primaryCI variable("pvChangeCI")
                                        approval variable("chgApproval")
                                        readyToDeploy variable("readyToDeploy")
                                    }
                                } 
                                custom("Release Readiness Check Preview"){
                                    script {
                                        type 'releaseScripts.completeReadinessGatePreview'
                                        codeReviewStatus variable("codeReviewStatus")
                                        qasTaskStatus variable("qasTaskStatus")
                                        pvchangeCategory variable("pvchangeCategory")
                                        pvChangePhase variable("pvChangePhase")
                                        pvChangeCI variable("pvChangeCI")
                                        ci variable("ci")
                                    }
                                }
                                gate('Release Readiness Check Preview'){
                                    team "EOC"
                                    tags "no skip"
                                    conditions{
                                        condition('Code Review Complete')
                                        condition('QAS Approval')
                                        condition('CI for the Release matches with the Change Record')
                                        condition('Change Record is a Standard Change and in the Execution Phase')
                                    }
                                }
                            } //end pvFlag && deployInSameChangeWindow == 'No' && useStandardChange
                            else{
                                userInput('Final Manual Check'){
                                    variables{
                                        if (pvFlag && deployInSameChangeWindow == 'No'){
                                            variable "pvChangeID"
                                        }
                                        else{
                                            variable "ChangeId"
                                        }
                                        variable "releaseChgInfo"
                                        variable "ci_code"
                                        variable "confirmCiMatches"
                                        variable "confirmChgWindow"
                                        variable "qasTaskStatus"
                                        variable "codeReviewStatus"
                                        variable "confirmCodeReview"
                                        variable "codeReviewCompletedBy"
                                        variable "codeDevelopers"
                                    }
                                    team "Change Analyst"
                                    watchers ('APP_XLR_CHG_MGT')
                                }
                                if (!ffUseAutomatedApprovalCheck){
                                    custom("Get Incident Details from ServiceNow"){
                                        precondition incPreCondition
                                        script {
                                            type 'servicenow.GetIncidentDetail'
                                            servicenowServer servicenowServer1
                                            incidentId variable("incidentID")
                                            incidentPriority variable("incidentPriority")
                                        }
                                    } 
                                    if (pvFlag && deployInSameChangeWindow == 'No'){
                                        custom("Get Change Details from ServiceNow"){
                                            precondition pvChangeIdPreCondition
                                            script {
                                                type 'servicenow.GetChangeDetail'
                                                servicenowServer servicenowServer1
                                                changeId variable("pvChangeID")
                                                changeCategory variable("pvchangeCategory")
                                                plannedStartDt variable("changeStartTimepv")
                                                plannedEndDt variable("changeEndTimepv")
                                                phase variable("pvChangePhase")
                                                primaryCI variable("pvChangeCI")
                                                approval variable("chgApproval")
                                                readyToDeploy variable("readyToDeploy")
                                            }
                                        } 
                                    } //end if
                                    else{
                                        custom("Get Change Details from ServiceNow"){
                                            precondition changeIdPreCondition
                                            script {
                                                type 'servicenow.GetChangeDetail'
                                                servicenowServer servicenowServer1
                                                changeId variable("ChangeId")
                                                changeCategory variable("changeCategory")
                                                plannedStartDt variable("changeStartTime")
                                                plannedEndDt variable("changeEndTime")
                                                phase variable("ChangePhase")
                                                primaryCI variable("ChangeCI")
                                                approval variable("chgApproval")
                                                readyToDeploy variable("readyToDeploy")
                                            }
                                        }  
                                    } //end else
                                } //end if not ffUseAutomatedApprovalCheck
                                if (ffUseAutomatedApprovalCheck){
                                    custom('Check for Change Approval and Ready to Deploy'){
                                        script {
                                            type "servicenow.CheckChangeApproval"
                                            servicenowServer servicenowServer1
                                            if (pvFlag && deployInSameChangeWindow == 'No'){
                                                changeId variable("pvChangeID")
                                            }
                                            else{
                                                changeId variable("ChangeId")
                                            }
                                            varChangePhase changeApprovalPhases
                                            pollInterval sm9PollInterval
                                        }
                                        team "EOC"
                                        watchers ('APP_XLR_CHG_MGT')
                                    }                                
                                }
                                if (ffUseCodeDeployTaskForStartTime){
                                    custom('Get Change Task Details'){
                                        script {
                                            type "servicenow.getChangeTaskDetails"
                                            servicenowServer servicenowServer1
                                            releaseNumber variable("theReleaseId")
                                            start_time variable("codeDeployStartTime")
                                            if (pvFlag && deployInSameChangeWindow == 'No'){
                                                changeId variable("pvChangeID")
                                            }
                                            else{
                                                changeId variable("ChangeId")
                                            }
                                            pollInterval sm9PollInterval
                                        }
                                        team "EOC"
                                        watchers ('APP_XLR_CHG_MGT')
                                    }
                                }
                                if (!ffUseAutomatedApprovalCheck){
                                    userInput('Schedule Deployment'){
                                        variables{
                                            if (pvFlag && deployInSameChangeWindow == 'No'){
                                                variable "pvChangeID"
                                                variable "changeStartTimepv"
                                                variable "changeEndTimepv"
                                            }
                                            else{
                                                variable "ChangeId"
                                                variable "changeStartTime"
                                                variable "changeEndTime"
                                            }
                                            variable "confirmApproved"
                                            variable "confirmChgWindow"

                                            variable "scheduleDeploy"
                                            if (ffUseCodeDeployTaskForStartTime) {
                                                variable "codeDeployStartTime"
                                            }
                                        }
                                        team "Change Analyst"
                                        watchers ('APP_XLR_CHG_MGT')
                                    }
                                }
                            }    
                            //add the task to actually schedule the release
                            if (!pvFlag){
                                custom('Update start date of prod deploy based on change start time'){
                                    script {
                                        type "releaseScripts.updateProdDeployStartDate"
                                        if (!ffUseCodeDeployTaskForStartTime){
                                            startDate variable("changeStartTime")
                                        }
                                        else{
                                            startDate variable("codeDeployStartTime")
                                        }
                                        scheduleDeploy variable("scheduleDeploy")
                                        envTag 'pr'
                                    }
                                }
                            }
                            else if (releaseType == 'Emergency' || deployInSameChangeWindow == 'Yes') {
                                custom('Update start date of prod deploy based on change start time') {
                                    script {
                                        type "releaseScripts.updateProdDeployStartDate"
                                        if (!ffUseCodeDeployTaskForStartTime){
                                            startDate variable("changeStartTime")
                                        }
                                        else{
                                            startDate variable("codeDeployStartTime")
                                        }
                                        scheduleDeploy variable("scheduleDeploy")
                                        envTag 'pv'
                                    }
                                }
                            }
                            else {
                                custom('Update start date of prod deploy based on change start time'){
                                    script {
                                        type "releaseScripts.updateProdDeployStartDate"
                                        if (!ffUseCodeDeployTaskForStartTime){
                                            startDate variable("changeStartTimepv")
                                        }
                                        else{
                                            startDate variable("codeDeployStartTime")
                                        }
                                        scheduleDeploy variable("scheduleDeploy")
                                        envTag 'pv'
                                    }
                                }
                            }//end else
                            custom("Tag release with Approved for Prod Deploy"){
                                script {
                                    type "releaseScripts.tagRelease"
                                    value "Approved"
                                    label "Deploy to PROD"
                                }
                            }
                        }//end else 
                        else{  //for recovery manager override
                            if (!pvFlag){
                                gate('Complete this task to start the Deployment to Prod'){
                                    team "EOC"
                                }
                            }
                            else {
                                gate('Complete this task to start the Deployment to Preview'){
                                    team "EOC"
                                }                          
                            }
                        }
                    }
                }
                phase("Production"){
                    tasks{
                        //preview deployment
                        if (pvFlag){
                            // not recovery manager
                            if (useRecoveryManager == 'No'){
                                // for emergency release or deploy in the same change window
                                if (releaseType == 'Emergency' || deployInSameChangeWindow == 'Yes'){
                                    custom("Get Change Details from ServiceNow"){
                                        script {
                                            type 'servicenow.GetChangeDetail'
                                            servicenowServer servicenowServer1
                                            changeId variable("ChangeId")
                                            changeCategory variable("changeCategory")
                                            plannedStartDt variable("changeStartTime")
                                            plannedEndDt variable("changeEndTime")
                                            phase variable("ChangePhase")
                                            primaryCI variable("ChangeCI")
                                            approval variable("chgApproval")
                                            readyToDeploy variable("readyToDeploy")
                                        }
                                        tags "pv"
                                        team "EOC"
                                    }
                                    custom ('Validate Preview Change Window') {
                                        script{
                                            type "releaseScripts.validateChangeWindow"
                                            changeStart variable("changeStartTime")
                                            changeEnd variable("changeEndTime")
                                            changeCategory variable("changeCategory")
                                            changePhase variable("ChangePhase")
                                            approval variable("chgApproval")
                                            readyToDeploy variable("readyToDeploy")
                                            envTag "pr"
                                            varChangePhase changeApprovalPhases
                                        }
                                        team "EOC"
                                        tags "validateWindow", "pv"
                                    }
                                } // end emergency release or deploy in same change window
                                else{ // deploy in separate change windows
                                    custom("Get Change Details from ServiceNow"){
                                        precondition pvChangeIdPreCondition
                                        script {
                                            type 'servicenow.GetChangeDetail'
                                            servicenowServer servicenowServer1
                                            changeId variable("pvChangeID")
                                            changeCategory variable("pvchangeCategory")
                                            plannedStartDt variable("changeStartTimepv")
                                            plannedEndDt variable("changeEndTimepv")
                                            phase variable("pvChangePhase")
                                            primaryCI variable("pvChangeCI")
                                            approval variable("chgApproval")
                                            readyToDeploy variable("readyToDeploy")
                                        }
                                        tags "pv"
                                        team "EOC"
                                    } 
                                    custom ('Validate Preview Change Window') {
                                        script{
                                            type "releaseScripts.validateChangeWindow"
                                            changeStart variable("changeStartTimepv")
                                            changeEnd variable("changeEndTimepv")
                                            changeCategory variable("pvchangeCategory")
                                            changePhase variable("pvChangePhase")
                                            approval variable("chgApproval")
                                            readyToDeploy variable("readyToDeploy")
                                            envTag "pr"
                                            varChangePhase changeApprovalPhases
                                        }
                                        team "EOC"
                                        tags "validateWindow", "pv"
                                    }
                                }
                            }
                            // always add deployment and validate tasks
                            for (appCode in fullApplicationPaths) {
                                custom('Deploy ' + appCode + ' to Preview') {
                                    script {
                                        type 'xldeploy.DeployTask'
                                        xldeployServer xldeployServer1
                                        continueIfStepFails false
                                        if (pauseDeploymentOnFail) {
                                            rollbackOnError false
                                            cancelOnError false
                                        }
                                        environment pvEnv
                                    }
                                team "EOC"
                                watchers ('APP-XLR-ADM')
                                tags "no skip", 'pv', appCode, "deploy"
                                }
                            }
                            gate ("Validate Preview") {
                                description "Complete this task to confirm that validation has been completed. Skip this task if validation was not completed. Please note that a skipped task may be escalated for approval prior to approving the production release."
                                tags "no skip"
                                team "tla00"
                            } // end deployment and validate tasks

                            //only add for not recovery manager
                            if (useRecoveryManager == 'No'){
                                // if it is a normal release and not dpeloying in the same change window
                                if (releaseType == "Normal" && deployInSameChangeWindow == "No"){
                                    sequentialGroup('Prod Deploy Group'){//a container for the prod readiness tasks
                                        tasks{
                                            userInput('Please input change and incident information for the Production Deployment') {
                                                variables {
                                                  variable "ChangeId"
                                                  variable "incidentID"
                                                  variable "scheduleDeploy"
                                                    }
                                                team "tla00"
                                                tags "no skip"
                                            }
                                            custom("Get Change Details from ServiceNow"){
                                                precondition changeIdPreCondition
                                                script {
                                                    type 'servicenow.GetChangeDetail'
                                                    servicenowServer servicenowServer1
                                                    changeId variable("ChangeId")
                                                    changeCategory variable("changeCategory")
                                                    plannedStartDt variable("changeStartTime")
                                                    plannedEndDt variable("changeEndTime")
                                                    phase variable("ChangePhase")
                                                    primaryCI variable("ChangeCI")
                                                    approval variable("chgApproval")
                                                    readyToDeploy variable("readyToDeploy")
                                                }
                                            }
                                            custom("Tag release with Change ID"){
                                                precondition changeIdPreCondition
                                                script {
                                                    type "releaseScripts.tagRelease"
                                                    value variable("ChangeId")
                                                    label " "
                                                }
                                            }
                                            custom("Tag release with Change Category"){
                                                precondition "changeCategory = getCurrentRelease().variablesByKeys['changeCategory']\nif changeCategory.value:\n\tresult = True\nelse:\n\tresult = False"
                                                script {
                                                    type "releaseScripts.tagRelease"
                                                    value variable("changeCategory")
                                                    label " "
                                                }
                                            }
                                            custom("Get Incident Details from ServiceNow"){
                                                precondition incPreCondition
                                                script {
                                                    type 'servicenow.GetIncidentDetail'
                                                    servicenowServer servicenowServer1
                                                    incidentId variable("incidentID")
                                                    incidentPriority variable("incidentPriority")
                                                }
                                            }                        
                                            custom("Tag release with Incident ID"){
                                                precondition incPreCondition
                                                script {
                                                    type "releaseScripts.tagRelease"
                                                    value variable("incidentID")
                                                    label " "
                                                }
                                            }
                                            // custom("Tag release with Incident Priority"){
                                            //     precondition "incidentPriority = getCurrentRelease().variablesByKeys['incidentPriority']\nif incidentPriority.value:\n\tresult = True\nelse:\n\tresult = False"
                                            //     script {
                                            //         type "releaseScripts.tagRelease"
                                            //         value variable("incidentPriority")
                                            //         label "Incident Priority"
                                            //     }
                                            // }
                                            sequentialGroup("Check for skips group"){
                                                tasks{
                                                    custom('check for skips'){
                                                        script{
                                                            type 'releaseScripts.checkForSkips'
                                                            email admEmail
                                                        }
                                                    }
                                                }
                                            } 
                                            userInput('Final Manual Check'){
                                                variables{
                                                    variable "ChangeId"
                                                    variable "releaseChgInfo"
                                                    variable "ci_code"
                                                    variable "confirmCiMatches"
                                                    variable "confirmChgWindow"
                                                    variable "qasTaskStatus"
                                                    variable "codeReviewStatus"
                                                    variable "confirmCodeReview"
                                                    variable "codeReviewCompletedBy"
                                                    variable "codeDevelopers"
                                                }
                                                team "Change Analyst"
                                                watchers ('APP_XLR_CHG_MGT')
                                            }
                                            if (!ffUseAutomatedApprovalCheck){
                                                custom("Get Change Details from ServiceNow"){
                                                    precondition changeIdPreCondition
                                                    script {
                                                        type 'servicenow.GetChangeDetail'
                                                        servicenowServer servicenowServer1
                                                        changeId variable("ChangeId")
                                                        changeCategory variable("changeCategory")
                                                        plannedStartDt variable("changeStartTime")
                                                        plannedEndDt variable("changeEndTime")
                                                        phase variable("ChangePhase")
                                                        primaryCI variable("ChangeCI")
                                                        approval variable("chgApproval")
                                                        readyToDeploy variable("readyToDeploy")
                                                    }
                                                }
                                                custom("Get Incident Details from ServiceNow"){
                                                    precondition incPreCondition
                                                    script {
                                                        type 'servicenow.GetIncidentDetail'
                                                        servicenowServer servicenowServer1
                                                        incidentId variable("incidentID")
                                                        incidentPriority variable("incidentPriority")
                                                    }
                                                } 
                                            }
                                            if (ffUseAutomatedApprovalCheck){
                                                custom('Check for Change Approval and Ready to Deploy'){
                                                    script {
                                                        type "servicenow.CheckChangeApproval"
                                                        servicenowServer servicenowServer1
                                                        changeId variable("ChangeId")
                                                        varChangePhase changeApprovalPhases
                                                        pollInterval sm9PollInterval
                                                    }
                                                    team "EOC"
                                                    watchers ('APP_XLR_CHG_MGT')
                                                }                                            
                                            }
                                            if (ffUseCodeDeployTaskForStartTime){
                                                custom('Get Change Task Details'){
                                                    script {
                                                        type "servicenow.getChangeTaskDetails"
                                                        servicenowServer servicenowServer1
                                                        releaseNumber variable("theReleaseId")
                                                        start_time variable("codeDeployStartTime")
                                                        changeId variable("ChangeId")
                                                        pollInterval sm9PollInterval
                                                    }
                                                    team "EOC"
                                                    watchers ('APP_XLR_CHG_MGT')
                                                }
                                            }                                            
                                            if (!ffUseAutomatedApprovalCheck){
                                                userInput('Schedule Production Deployment'){
                                                    variables{
                                                        variable "ChangeId"
                                                        variable "confirmApproved"
                                                        variable "confirmChgWindow"
                                                        variable "changeStartTime"
                                                        variable "changeEndTime"
                                                        variable "scheduleDeploy"
                                                    }
                                                    team "Change Analyst"
                                                    watchers ('APP_XLR_CHG_MGT')
                                                } 
                                            }
                                            sequentialGroup("Update Start Time Group"){
                                                tasks{
                                                    custom('Update start date of prod deploy based on change start time'){
                                                        script {
                                                            type "releaseScripts.updateProdDeployStartDate"
                                                            if (!ffUseCodeDeployTaskForStartTime){
                                                                startDate variable("changeStartTime")
                                                            }
                                                            else{
                                                                startDate variable("codeDeployStartTime")
                                                            }
                                                            scheduleDeploy variable("scheduleDeploy")
                                                            envTag 'pr'
                                                        }
                                                    }                                            
                                                }
                                            }
                                        }//end tasks for prod deploy group
                                    }//end prod deploy group                                  
                                }//end normal relase not in the same change window
                            }// end not recovery manager
                            else { //recovery manager
                                gate('Complete this task to start the Deployment to Prod'){
                                    team "EOC"
                                }
                            } // end recovery manager
                        } //end preview deployment

                        //prod deployment
                        if (prEnv != "None"){
                            if (useRecoveryManager == 'No'){
                                custom("Get Change Details from ServiceNow"){
                                    script {
                                        type 'servicenow.GetChangeDetail'
                                        servicenowServer servicenowServer1
                                        changeId variable("ChangeId")
                                        changeCategory variable("changeCategory")
                                        plannedStartDt variable("changeStartTime")
                                        plannedEndDt variable("changeEndTime")
                                        phase variable("ChangePhase")
                                        primaryCI variable("ChangeCI")
                                        approval variable("chgApproval")
                                        readyToDeploy variable("readyToDeploy")
                                    }
                                    tags "pr"
                                    team "EOC"
                                }
                                custom ('Validate Prod Change Window') {
                                    script{
                                        type "releaseScripts.validateChangeWindow"
                                        changeStart variable("changeStartTime")
                                        changeEnd variable("changeEndTime")
                                        changeCategory variable("changeCategory")
                                        changePhase variable("ChangePhase")
                                        approval variable("chgApproval")
                                        readyToDeploy variable("readyToDeploy")
                                        envTag "pr"
                                        varChangePhase changeApprovalPhases
                                    }
                                    team "EOC"
                                    tags "validateWindow", "pr"
                                }
                                // TODO BROKEN NEED TO FIX 
                                // custom('XLD task to get prod versions'){
                                //     script{
                                //         type 'xldeploy.getProdVersions'
                                //         appList variable('fullApplicationPaths')
                                //         prodVersions variable('prodVersions')
                                //         envList 'test'
                                //     }
                                //     team 'tla00'
                                // }
                            }// end recovery manager = no

                            // always add deployment and validate tasks
                            for (appCode in fullApplicationPaths) {
                                custom('Deploy ' + appCode + ' to Production') {
                                    script {
                                        type 'xldeploy.DeployTask'
                                        xldeployServer xldeployServer1
                                        continueIfStepFails false
                                        environment prEnv
                                        if (pauseDeploymentOnFail) {
                                            rollbackOnError false
                                            cancelOnError false
                                        }
                                    }
                                    team "EOC"
                                    tags "no skip", 'pr', appCode, "deploy"
                                    watchers ('APP-XLR-ADM')
                                }
                            }//app code loop
                            gate ("Validate Production") {
                                description "Complete this task to confirm that validation has been completed. Skip this task if validation was not completed. Please note that a skipped task may be escalated for approval prior to approving the production release."
                                tags "no skip"
                                team "tla00"
                            } // end deployment and validate tasks                                              
                        }
                        // end prod deployment

                        // dr deployment
                        if (drEnv != "None"){
                            // always add deployment and validate tasks
                            for (appCode in fullApplicationPaths) {
                                custom('Deploy ' + appCode + ' to DR') {
                                    script {
                                      type 'xldeploy.DeployTask'
                                      xldeployServer xldeployServer1
                                      continueIfStepFails false
                                      environment drEnv
                                    }
                                    team "EOC"
                                    tags "no skip", 'dr', appCode, "deploy"
                                }
                            }//app code loop
                            gate ("Validate DR") {
                                description "Complete this task to confirm that validation has been completed. Skip this task if validation was not completed. Please note that a skipped task may be escalated for approval prior to approving the production release."
                                tags "no skip"
                                team "tla00"
                            } // end deployment and validate tasks  
                        }
                        //end dr deployment
                    } // end of tasks
                } // end of prod phase
                phase("Post Prod"){
                    tasks{
                        if (useRecoveryManager == 'Yes'){
                            userInput('Please input change and incident information') {
                                variables {
                                  variable "ChangeId"
                                  variable "incidentID"
                                    }
                                team "tla00"
                                tags "no skip"
                            }                        
                        }// end recovery manager
                        if (useRecoveryManager == 'Yes' || releaseType == 'Emergency'){
                            if (isCodeReviewRequired || isQASRequired){
                                parallelGroup('Review Gates'){
                                    tasks {
                                        if (isCodeReviewRequired){
                                            sequentialGroup('Code Review Group'){
                                                tasks{
                                                    userInput('Please enter the code developer for the release') {
                                                        variables {
                                                          variable "codeDevelopers"
                                                        }
                                                        team "tla00"
                                                        tags "no skip"
                                                    }
                                                    custom("Tag release with Code Review Eligible"){
                                                        script {
                                                            type "releaseScripts.tagRelease"
                                                            value "Eligible"
                                                            label "Code Review"
                                                        }
                                                    }
                                                    gate('Code Review Questions'){
                                                        //need to get the watchers list for this task
                                                        team "tla00_app_support"
                                                        tags "no skip"
                                                        conditions{
                                                            condition('Confirm that code does not implement vulnerabilities that could be exploited later')
                                                            condition('Confirm that code does not implement changes that could compromise customer or employee privacy')
                                                            condition('Confirm that code does not inappropriately modify customer, account, or transaction information')
                                                        }
                                                    }
                                                    custom('Get Code Review Task Info'){
                                                        script{
                                                            type "releaseScripts.getTaskInfo"
                                                            xlreleaseServer xlreleaseServer1
                                                            taskTitle "Code Review Questions"
                                                            taskOwner variable('codeReviewCompletedBy')
                                                            taskStatus variable('codeReviewStatus')
                                                            taskCompleteDate variable('codeReviewCompleteDate')
                                                        }
                                                    }
                                                    custom("Remove tag from release - Code Review Eligible"){
                                                        script {
                                                            type "releaseScripts.deleteReleaseTag"
                                                            delTag "Code Review: Eligible"
                                                        }
                                                    }
                                                    custom("Tag release with Code Review Complete"){
                                                        script {
                                                            type "releaseScripts.tagRelease"
                                                            value variable('codeReviewStatus')
                                                            label "Code Review"
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (isQASRequired){
                                            sequentialGroup('Ask QAS Questions'){
                                                tasks{
                                                    parallelGroup('Ask Yes / No Questions'){
                                                        tasks{
                                                            userInput('Has security testing been executed?') {
                                                                variables {
                                                                  variable "secTestYesNo"
                                                                    }
                                                                team qasTeam
                                                                tags "no skip"
                                                                watchers ('APP_XLR_QAS')
                                                            }
                                                            userInput('Has performance testing been executed?') {
                                                                variables {
                                                                  variable "perfTestYesNo"
                                                                    }
                                                                team qasTeam
                                                                tags "no skip"
                                                                watchers ('APP_XLR_QAS')
                                                            }
                                                            userInput('Has automated testing been executed?') {
                                                                variables {
                                                                  variable "automatedTestYesNo"
                                                                    }
                                                                team qasTeam
                                                                tags "no skip"
                                                                watchers ('APP_XLR_QAS')
                                                            }
                                                            userInput('Has functional Testing been executed?') {
                                                                variables {
                                                                  variable "functionTestYesNo"
                                                                    }
                                                                team qasTeam
                                                                tags "no skip"
                                                                watchers ('APP_XLR_QAS')
                                                            }                                                                                                                                                
                                                        }
                                                    }
                                                    custom("Script to ask follow up QAS questions"){
                                                        script{
                                                            type "releaseScripts.qasQuestions"
                                                        }
                                                    }
                                                    parallelGroup('QAS Follow Up Questions'){

                                                    }
                                                }
                                            }//qas group
                                        }// end QAS                                    
                                    }//end tasks
                                }//end review gates
                            }//end if code review or qas required
                            sequentialGroup("Check for skips group"){
                                tasks{
                                    custom('check for skips'){
                                        script{
                                            type 'releaseScripts.checkForSkips'
                                            email admEmail
                                        }
                                    }
                                }
                            }//end group                        
                        } //end recovery manager or emergency
                        custom('Get Prod Deploy Times'){//return the prod deploy start and end times for the change analyst....someday push this back to ServiceNow
                            script{
                                type 'releaseScripts.getProdDeployTimes'
                            }
                        }
                        if (useRecoveryManager == 'Yes' || releaseType == 'Emergency'){//do the PIR
                            userInput('Post Implementation Review'){
                                variables{
                                    variable "ChangeId"
                                    variable "confirmApproved"
                                    variable "releaseChgInfo"
                                    variable "confirmCiMatches"
                                    variable "confirmDeploymentTime"
                                    variable "prodDeployTimes"
                                    variable "changeStartTime"
                                    variable "changeEndTime"
                                    variable "incidentPriority"
                                    variable "codeReviewStatus"
                                    variable "qasTaskStatus"
                                    variable "exceptionViolation"
                                    variable "exceptionViolationReason"
                                    variable "reviewSummary"
                                }
                                team "Change Analyst"
                                watchers ('APP_XLR_CHG_MGT')
                            }
                        }//end if
                        if (isXld){
                            custom('Update Package Name'){//update the package to with -PROD in XLD
                                script{
                                    type 'xldeploy.renameReleasedPackages'
                                    xldeployServer xldeployServer1
                                    varNameList variable('appToPackageMap')
                                }
                            }
                        }//end if
                    }//end tasks
                }//end post prod phase
            }//end if (prEnv != "None")
        }//end phases
    } //end release
}//end xlr