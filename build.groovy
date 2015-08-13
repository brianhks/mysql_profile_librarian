import com.github.jknack.semver.AndExpression
import com.github.jknack.semver.RelationalOp
import com.github.jknack.semver.Semver
import groovy.json.JsonSlurper
import org.rauschig.jarchivelib.Archiver
import org.rauschig.jarchivelib.ArchiverFactory
import tablesaw.*
import tablesaw.addons.ivy.ResolveRule
import tablesaw.addons.ivy.RetrieveRule
import tablesaw.rules.*
import tablesaw.addons.*
import tablesaw.addons.ivy.PomRule
import tablesaw.addons.ivy.PublishRule

saw = Tablesaw.getCurrentTablesaw()
saw.includeDefinitionFile("definitions.xml")


modules = saw.getProperty("modules").tokenize(',')
modulesDir = saw.getProperty("modules_directory")
buildDir = saw.getProperty("build_directory")
libDir = saw.getProperty("lib_directory")
defaultResolver = saw.getProperty("resolver", null)

//The following are used when packaging the modules together
projectName = saw.getProperty("project_name")
projectPackage = saw.getProperty("project_package")
projectVersion = saw.getProperty("project_version")

ivySettings = [new File("ivysettings.xml")]
if (saw.getProperty("ivysettings") != null)
	ivySettings.add(new File(saw.getProperty("ivysettings")))

buildDirRule = new DirectoryRule(buildDir)
libDirRule = new DirectoryRule(libDir)


//==============================================================================
//r10k Resolve
resolveRule = new SimpleRule("resolve").setDescription("Resolve modules with r10k")
		.setMakeAction("doResolve")
		.addDepend(libDirRule)

def doResolve(Rule rule)
{
	librarianDef = saw.getDefinition("librarian-puppet")
	librarianDef.set("install")
	librarianDef.set("path", libDir)

	saw.exec(librarianDef.getCommand())
	/*r10kDef = saw.getDefinition("r10k-install")
	r10kDef.set("moduledir", new File(libDir).getAbsolutePath())
	r10kDef.set("puppetfile", new File("Puppetfile").getAbsolutePath())

	saw.exec("/home/bhawkins/work/r10k", r10kDef.getCommand(), true);*/
}


//==============================================================================
//Rule to package all modules and dependencies.
packageTarRule = new TarRule("${buildDir}/${projectName}_v${projectVersion}.tar")
		.addFileSet(new RegExFileSet(modulesDir, ".*").recurse())
		.addFileSet(new RegExFileSet(libDir, ".*").recurse())
		.addDepend(buildDirRule)
		.addDepend("resolve")

packageGzipRule = new GZipRule("package").setSource(packageTarRule.getTarget())
		.setDescription("Package all modules and dependencies into one artifact.")
		.setTarget("${buildDir}/${projectName}_v${projectVersion}.tar.gz")
		.addDepend(packageTarRule)

//setup publish for module
packageDefaultResolver = "nexus-releases"
packageIvyFile = new File("ivy.xml")

def packageResolveRule = new ResolveRule(packageIvyFile, ivySettings, Collections.singleton("*"))
packageResolveRule.setName(null)

def packagePublishRule = new PublishRule(packageIvyFile, packageDefaultResolver, packageResolveRule)
		.setName("publish-package")
		.setDescription("Publish package to repo")
		.setArtifactId(projectName)
		.setGroupId(projectPackage)
		.setVersion(projectVersion)
		.addDepend(buildDirRule)
		.setOverwrite(false)

//Add Pom file to artifacts
packagePublishRule.addArtifact(packageGzipRule.getTarget())
		.setType("module")
		.setExt("tar.gz")


/*packageRule = new ZipRule("package", "${buildDir}/${projectName}_v${projectVersion}.zip")
		.setDescription("Package all modules and dependencies into one artifact")
		.addDepend("retrieve-all")
		.addFileSet(new RegExFileSet(modulesDir, ".*").recurse())
		.addFileSet(new RegExFileSet(libDir, ".*").recurse())*/


//==============================================================================
//Rule for running lint on all pp files
ppFiles = new RegExFileSet(modulesDir, ".*\\.pp").recurse()
		.getFullFilePaths()

lintRule = new SimpleRule("puppet-lint").setDescription("Run puppet-lint on all puppet files")
		.addSources(ppFiles)
		.setMakeAction("runLint")
		
void runLint(Rule rule)
{
	lintDef = saw.getDefinition("puppet-lint")
	//lintDef.set("fail-on-warnings")
	
	for (String source : rule.getSources())
	{
		lintDef.set("manifest", source)
		saw.exec(lintDef.getCommand())
	}
}

void prepForTarget(String target)
{
	println target
	if (target.startsWith("publish"))
		filterLocalModules = false
}


