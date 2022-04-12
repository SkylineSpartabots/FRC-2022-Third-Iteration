package frc.robot.commands.CAS;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.filter.SlewRateLimiter;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.CommandBase;
import edu.wpi.first.wpilibj2.command.ProfiledPIDCommand;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import edu.wpi.first.wpilibj2.command.WaitCommand;
import frc.lib.util.Controller;
import frc.robot.Constants;
import frc.robot.RobotContainer;
import frc.robot.Constants.DriveConstants;
import frc.robot.commands.TeleopDriveCommand;
import frc.robot.commands.SetSubsystemCommand.SetIndexerCommand;
import frc.robot.commands.SetSubsystemCommand.SetIntakeCommand;
import frc.robot.subsystems.DrivetrainSubsystem;
import frc.robot.subsystems.IndexerSubsystem;
import frc.robot.subsystems.LimelightSubsystem;
import frc.robot.subsystems.ShooterSubsystem;

public class MemeShoot extends TeleopDriveCommand{ //REPLACABLE BY AIM SEQUENCE
    private PIDController m_thetaController;
    private Translation2d m_targetPosition = Constants.targetHudPosition;
    private final ShooterSubsystem m_shooter;

    public MemeShoot() {
        super(DrivetrainSubsystem.getInstance());
        m_shooter = ShooterSubsystem.getInstance();
        isIndexerOn = false;
        addRequirements(m_shooter, IndexerSubsystem.getInstance());
       
        m_thetaController = new PIDController(3,0,0);
        m_thetaController.enableContinuousInput(-Math.PI, Math.PI);
        m_timer =  new Timer();
        
    }
    private Timer m_timer;
    private double firstTurnTime;//can be calculated for change
    private double shootTime;
    private double finish;


    //on start: set turn target (projected to be 0.6 seconds into the future: lock velocity)
    //on 0.4 seconds: start firing
    //on 0.6 seconds: assume first ball has fired. set new target for second
    //0.8: unlock wheels
    
    double xSpeedFiltered;
    double ySpeedFiltered;
    double targetAngle;
    @Override
    public void initialize(){
        m_timer =  new Timer();
        firstTurnTime = 0.8;//can be calculated for change
        shootTime = firstTurnTime - 0.4;
        finish = firstTurnTime + 0.5;

        m_timer.reset();
        m_timer.start();

        /*double maxSpeed = 1.0;
        if(Math.abs(xSpeedFiltered) > maxSpeed) xSpeedFiltered = Math.copySign(maxSpeed, xSpeedFiltered);
        if(Math.abs(ySpeedFiltered) > maxSpeed) ySpeedFiltered = Math.copySign(maxSpeed, ySpeedFiltered);*/

        var xSpeed = -modifyAxis(m_controller.getLeftY()) * DriveConstants.kMaxSpeedMetersPerSecond;
        var ySpeed = -modifyAxis(m_controller.getLeftX()) * DriveConstants.kMaxSpeedMetersPerSecond;
        xSpeedFiltered = xSpeed;//driveXFilter.calculate(xSpeed);
        ySpeedFiltered = ySpeed;// driveYFilter.calculate(ySpeed);
        
        
        
        double xPosition = m_drivetrainSubsystem.getPose().getX() + firstTurnTime * xSpeedFiltered;
        double yPosition = m_drivetrainSubsystem.getPose().getY() + firstTurnTime * ySpeedFiltered;
        SmartDashboard.putNumber("1 x projected X", xPosition);
        SmartDashboard.putNumber("1 y projected Y", yPosition);
        SmartDashboard.putNumber("1 x hub", m_targetPosition.getX());
        SmartDashboard.putNumber("1 x hub", m_targetPosition.getY());
        SmartDashboard.putNumber("1 speed filtered", m_targetPosition.getX());
        SmartDashboard.putNumber("1 speed filtered", m_targetPosition.getY());

        
        double time = 1.0;
        m_targetPosition = new Translation2d(
            Constants.targetHudPosition.getX() - xSpeedFiltered * time, 
            Constants.targetHudPosition.getY() - ySpeedFiltered * time);


        targetAngle = Math.toRadians(DrivetrainSubsystem.findAngle(
            new Pose2d(
            xPosition, 
            yPosition, 
            m_drivetrainSubsystem.getPose().getRotation()), 
            m_targetPosition.getX(), 
            m_targetPosition.getY(), 180));
        

        double distance = DrivetrainSubsystem.calculateDistance(
            xPosition, 
            yPosition, 
            m_targetPosition.getX(), 
            m_targetPosition.getY());
        shooterSpeed = calculateShooterSpeed(distance);

        SmartDashboard.putNumber("1 angle", Math.toDegrees(targetAngle));
        SmartDashboard.putNumber("1 distance", distance);
        SmartDashboard.putNumber("1 shooter", shooterSpeed);
        
    }
    
    @Override
    public void execute() {

        SmartDashboard.putNumber("Time", m_timer.get());
        driveWithJoystick();

        if(m_timer.hasElapsed(shootTime)){
            //fire indexer
            shootTime = 1000000;
            IndexerSubsystem.getInstance().setIndexerPercentPower(0.3, false);
            IndexerSubsystem.getInstance().setIntakePercentPower(Constants.intakeOn, false);
        }
        
        if(m_timer.hasElapsed(firstTurnTime)){

            
        int currentVel = m_shooter.getVelocity();
        int threshold = 170; 
        double angleDiff = Math.toDegrees(m_drivetrainSubsystem.getGyroscopeRotation().getRadians() - targetAngle);

        boolean isFacingTarget = Math.abs(angleDiff) < 3.0;
        boolean isShooterAtSpeed = (currentVel >= shooterSpeed - threshold && currentVel <= shooterSpeed + threshold);
        boolean isReadyToShoot = isShooterAtSpeed && shooterWithinBounds && isFacingTarget; //&& isRobotNotMoving;

        DrivetrainSubsystem.getInstance().setHubPosition(m_targetPosition.getX(), m_targetPosition.getY());

        SmartDashboard.putNumber("1 shooter speed", shooterSpeed);
        SmartDashboard.putBoolean("1 ?Facing Target", isFacingTarget);
        SmartDashboard.putBoolean("1 ?Shooter At Speed", isShooterAtSpeed);
        SmartDashboard.putBoolean("1 ?Indexer Off", !isIndexerOn);
        SmartDashboard.putBoolean("1 ?shooter within bounds", shooterWithinBounds);
        SmartDashboard.putBoolean("1 ?Ready To Shoot", isReadyToShoot);


            //calculate second turn
            double xPosition = m_drivetrainSubsystem.getPose().getX() + (finish - firstTurnTime) * xSpeedFiltered;
            double yPosition = m_drivetrainSubsystem.getPose().getY() + (finish - firstTurnTime) * ySpeedFiltered;

            targetAngle = Math.toRadians(DrivetrainSubsystem.findAngle(
            new Pose2d(
                xPosition, 
                yPosition, 
                m_drivetrainSubsystem.getPose().getRotation()), 
                m_targetPosition.getX(), 
                m_targetPosition.getY(), 180));  

            shooterSpeed = calculateShooterSpeed(DrivetrainSubsystem.calculateDistance(
                xPosition, 
                yPosition, 
                m_targetPosition.getX(), 
                m_targetPosition.getY()));

            
                firstTurnTime = 1000000;
        }
    }
    
    @Override
    public boolean isFinished() {
        return m_timer.hasElapsed(finish);
    }
    @Override
    public void end(boolean interruptable){  
        
        int currentVel = m_shooter.getVelocity();
        int threshold = 170; 
        double angleDiff = Math.toDegrees(m_drivetrainSubsystem.getGyroscopeRotation().getRadians() - targetAngle);

        boolean isFacingTarget = Math.abs(angleDiff) < 3.0;
        boolean isShooterAtSpeed = (currentVel >= shooterSpeed - threshold && currentVel <= shooterSpeed + threshold);
        boolean isReadyToShoot = isShooterAtSpeed && shooterWithinBounds && isFacingTarget; //&& isRobotNotMoving;

        DrivetrainSubsystem.getInstance().setHubPosition(m_targetPosition.getX(), m_targetPosition.getY());

        /*SmartDashboard.putBoolean("2 ?Facing Target", isFacingTarget);
        SmartDashboard.putBoolean("2 ?Shooter At Speed", isShooterAtSpeed);
        SmartDashboard.putBoolean("2 ?Indexer Off", !isIndexerOn);
        SmartDashboard.putBoolean("2 ?shooter within bounds", shooterWithinBounds);
        SmartDashboard.putBoolean("2 ?Ready To Shoot", isReadyToShoot);*/

        IndexerSubsystem.getInstance().setIndexerPercentPower(Constants.indexerUp, true);
        IndexerSubsystem.getInstance().setIntakePercentPower(Constants.intakeOn, true);
        ShooterSubsystem.getInstance().setShooterVelocity(Constants.shooterIdle);        
    }

    boolean isIndexerOn = false;
    boolean hasRobertShotBall = false;
    boolean shooterWithinBounds = false;
    int shooterSpeed;

    @Override
    public void driveWithJoystick() {//called periodically
        
        // this code is so amazing -atharv
        double currentRotation = m_drivetrainSubsystem.getGyroscopeRotation().getRadians();
        double rot = m_thetaController.calculate(currentRotation,targetAngle);
        
        if(m_timer.hasElapsed(firstTurnTime)){
            m_shooter.setShooterVelocity(shooterSpeed+200);
        }
        else{
            m_shooter.setShooterVelocity(shooterSpeed+00);
        }
        
        m_drivetrainSubsystem.drive(ChassisSpeeds.fromFieldRelativeSpeeds(xSpeedFiltered, ySpeedFiltered, 
                rotFilter.calculate(rot), m_drivetrainSubsystem.getGyroscopeRotation()));
                //rot, m_drivetrainSubsystem.getGyroscopeRotation()));
    }
    

    private int calculateShooterSpeed(double distance){

        double shooterSlope = 1099;
        double shooterIntercept = 6500.0;
  
        double minVelocity = 8000;
        double maxVelocity = 14000;
  
        
        double targetShooterVelocity = shooterSlope * distance + shooterIntercept;
        shooterWithinBounds = true;
        if(targetShooterVelocity > maxVelocity) {
          targetShooterVelocity = maxVelocity;
          shooterWithinBounds = false;
        }
        else if(targetShooterVelocity < minVelocity){
          targetShooterVelocity = minVelocity;
          shooterWithinBounds = false;
        }
          
        return (int)targetShooterVelocity;
      }

}