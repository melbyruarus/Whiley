package wyocl.util;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.List;

import wybs.lang.Content;
import wybs.lang.Logger;
import wybs.lang.Path;
import wybs.util.DirectoryRoot;
import wybs.util.StandardBuildRule;
import wybs.util.StandardProject;
import wyc.util.WycBuildTask;
import wyil.lang.WyilFile;
import wyjc.util.WyjcBuildTask;
import wyjvm.lang.ClassFile;
import wyocl.builders.Wyil2JavaBuilder;
import wyocl.builders.Wyil2OpenClBuilder;
import wyocl.lang.ClFile;

/**
 * Responsible for controlling the building of JVM Class files using the
 * WyoclBuilder. It pretty much just defers everything to the existing
 * <code>WyjcBuildTask</code>, but using a different builder.
 * 
 * @author David J. Pearce
 * 
 */
public class WyoclBuildTask extends WycBuildTask {
	
	public static class Registry extends WyjcBuildTask.Registry {
		@Override
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public void associate(Path.Entry e) {
			String suffix = e.suffix();
			
			if(suffix.equals("cl")) {				
				e.associate(ClFile.ContentType, null);				
			} else {
				super.associate(e);
			}
		}
		
		@Override
		public String suffix(Content.Type<?> t) {
			if(t == ClFile.ContentType) {
				return "cl";
			} else {
				return super.suffix(t);
			}
		}
	}
		
	/**
	 * The purpose of the class file filter is simply to ensure only binary
	 * files are loaded in a given directory root. It is not strictly necessary
	 * for correct operation, although hopefully it offers some performance
	 * benefits.
	 */
	public static final FileFilter clFileFilter = new FileFilter() {
		@Override
		public boolean accept(File f) {
			String name = f.getName();
			return name.endsWith(".cl") || f.isDirectory();
		}
	};
	
	/**
	 * The class directory is the filesystem directory where all generated jvm
	 * class files are stored.
	 */
	protected DirectoryRoot classDir;
	
	/**
	 * The class directory is the filesystem directory where all generated cl
	 * files are stored.
	 */
	protected DirectoryRoot clDir;
	
	public WyoclBuildTask() {
		super(new Registry());
	}
	
	@Override
	public void setWhileyDir(File dir) throws IOException {
		// Note, we don't call super.setWhileyDir here as might be expected.
		// This is because that would set the wyilDir to a matching directory
		// root. However, for this builder, we don't want to write wyil files by
		// default.
		this.whileyDir = new DirectoryRoot(dir, whileyFileFilter, registry);
		if (classDir == null) {
			this.classDir = new DirectoryRoot(dir, WyjcBuildTask.classFileFilter,
					registry);
		}
		if (clDir == null) {
			this.clDir = new DirectoryRoot(dir, clFileFilter, registry);
		}
	}
	
	@Override
	public void setWyilDir(File dir) throws IOException {
		super.setWyilDir(dir);
		if (classDir == null) {
			this.classDir = new DirectoryRoot(dir, WyjcBuildTask.classFileFilter,
					registry);
		}
		if (clDir == null) {
			this.clDir = new DirectoryRoot(dir, clFileFilter, registry);
		}
	}

	public void setClassDir(File classdir) throws IOException {
		this.classDir = new DirectoryRoot(classdir, WyjcBuildTask.classFileFilter,
				registry);
	}
	
	public void setClDir(File cldir) throws IOException {
		this.clDir = new DirectoryRoot(cldir, clFileFilter, registry);
	}
	
	@Override
	protected void addBuildRules(StandardProject project) {
		
		// Add default build rule for converting whiley files into wyil files. 
		super.addBuildRules(project);
		
		// =======================================================
		// Wyil => Class
		// =======================================================
		
		Wyil2JavaBuilder jBuilder = new Wyil2JavaBuilder();

		if (verbose) {
			jBuilder.setLogger(new Logger.Default(System.err));
		}

		StandardBuildRule rule = new StandardBuildRule(jBuilder);
		
		rule.add(wyilDir, wyilIncludes, wyilExcludes, classDir,
				WyilFile.ContentType, ClassFile.ContentType);

		project.add(rule);
		
		// =======================================================
		// Wyil => CL
		// =======================================================

		Wyil2OpenClBuilder clBuilder = new Wyil2OpenClBuilder();

		if (verbose) {
			clBuilder.setLogger(new Logger.Default(System.err));
		}

		rule = new StandardBuildRule(clBuilder);
		
		rule.add(wyilDir, wyilIncludes, wyilExcludes, clDir,
				WyilFile.ContentType, ClFile.ContentType);

		project.add(rule);
		
	}
	
	@Override
	protected List<Path.Entry<?>> getModifiedSourceFiles() throws IOException {
		// First, determine all whiley source files which are out-of-date with
		// respect to their wyil files.
		@SuppressWarnings("unchecked")
		List<Path.Entry<?>> sources = super.getModifiedSourceFiles();

		// Second, look for any wyil files which are out-of-date with their
		// respective class file.
		for (Path.Entry<WyilFile> source : wyilDir.get(wyilIncludes)) {
			Path.Entry<ClassFile> binary = classDir.get(source.id(),
					ClassFile.ContentType);

			// first, check whether wyil file out-of-date with source file
			if (binary == null || binary.lastModified() < source.lastModified()) {
				sources.add(source);
			}
		}

		// done
		return sources;
	}
	
	@Override
	protected void flush() throws IOException {
		super.flush();
		classDir.flush();
		clDir.flush();
	}	
}
